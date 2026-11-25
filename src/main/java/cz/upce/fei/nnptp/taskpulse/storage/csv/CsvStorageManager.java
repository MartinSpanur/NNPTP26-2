package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.storage.IWorkspaceUnitOfWork;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Persists a workspace as CSV files. Row conversion is delegated to {@link CsvEntityMapper} implementations;
 * this class is responsible for file I/O and for validating relationships between entities.
 */
public class CsvStorageManager {
    private static final String UTF8_BOM = "\uFEFF";

    private final Path dataDirectory;
    private final ProjectCsvMapper projectMapper = new ProjectCsvMapper();
    private final TaskCsvMapper taskMapper = new TaskCsvMapper();
    private final WorklogCsvMapper worklogMapper = new WorklogCsvMapper();
    private final WorkspaceSnapshotFactory snapshotFactory =
            new WorkspaceSnapshotFactory(projectMapper, taskMapper, worklogMapper);
    private final WorkspaceChangeDetector changeDetector = new WorkspaceChangeDetector();
    private final WorkspaceFileOperations fileOperations;

    public CsvStorageManager(Path dataDirectory) {
        this(dataDirectory, new WorkspaceFileOperations());
    }

    CsvStorageManager(
        Path dataDirectory,
        WorkspaceFileOperations fileOperations
    ) {
        this.dataDirectory = Objects.requireNonNull(
                dataDirectory,
                "Data directory must not be null"
        ).toAbsolutePath().normalize();

        this.fileOperations = Objects.requireNonNull(
                fileOperations,
                "File operations must not be null"
        );
    }

    private record LoadedWorkspace(ProjectRepository projectRepository, TaskRepository taskRepository) {}

    public IWorkspaceUnitOfWork openUnitOfWork() {
        LoadedWorkspace workspace = loadWorkspace();
        WorkspaceSnapshot originalSnapshot = snapshotFactory.create(
                workspace.projectRepository(),
                workspace.taskRepository()
        );

        return new CsvWorkspaceUnitOfWork(
                this,
                workspace.projectRepository(),
                workspace.taskRepository(),
                snapshotFactory,
                changeDetector,
                originalSnapshot
        );
    }

    private LoadedWorkspace loadWorkspace() {
        return loadWorkspaceFrom(dataDirectory);
    }

    private LoadedWorkspace loadWorkspaceFrom(Path sourceDirectory) {
        InMemoryProjectRepository projectRepo = new InMemoryProjectRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        if (!Files.exists(sourceDirectory)) {
            return new LoadedWorkspace(projectRepo, taskRepo);
        }
        if (!Files.isDirectory(sourceDirectory)) {
            throw new StorageException("Workspace path is not a directory: " + sourceDirectory);
        }

        Set<UUID> projectIds = new HashSet<>();
        readAll(sourceDirectory, projectMapper, project -> {
            if (!projectIds.add(project.getId())) {
                throw new StorageException("Duplicate project id detected: " + project.getId());
            }
            projectRepo.save(project);
        });

        Map<UUID, Task> tasksById = new HashMap<>();
        readAll(sourceDirectory, taskMapper, task -> {
            if (!projectIds.contains(task.getProjectId())) {
                throw new StorageException("Task references missing project: " + task.getProjectId());
            }
            if (tasksById.putIfAbsent(task.getId(), task) != null) {
                throw new StorageException("Duplicate task id detected: " + task.getId());
            }
            taskRepo.save(task);
        });

        readAll(sourceDirectory, worklogMapper, taskWorklog -> {
            Task task = tasksById.get(taskWorklog.taskId());
            if (task == null) {
                throw new StorageException("Worklog references missing task: " + taskWorklog.taskId());
            }
            task.restoreWorklog(taskWorklog.worklog());
        });

        return new LoadedWorkspace(projectRepo, taskRepo);
    }

    void commitAtomically(
        ProjectRepository projectRepository,
        TaskRepository taskRepository
    ) {
        Objects.requireNonNull(
                projectRepository,
                "Project repository must not be null"
        );
        Objects.requireNonNull(
                taskRepository,
                "Task repository must not be null"
        );

        Path stagingDirectory = siblingDirectory("stage");
        Path backupDirectory = siblingDirectory("backup");

        boolean liveWorkspaceMoved = false;
        boolean newWorkspaceActivated = false;

        try {
            writeWorkspace(
                    stagingDirectory,
                    projectRepository,
                    taskRepository
            );

            validateWorkspace(stagingDirectory);

            if (Files.exists(dataDirectory)) {
                fileOperations.moveAtomically(
                        dataDirectory,
                        backupDirectory
                );
                liveWorkspaceMoved = true;
            }

            fileOperations.moveAtomically(
                    stagingDirectory,
                    dataDirectory
            );
            newWorkspaceActivated = true;
        } catch (Exception commitFailure) {
            StorageException failure = asStorageException(
                    "Failed to commit workspace atomically",
                    commitFailure
            );

            try {
                fileOperations.deleteDirectoryIfExists(
                        stagingDirectory
                );

                if (liveWorkspaceMoved
                        && !newWorkspaceActivated) {

                    fileOperations.deleteDirectoryIfExists(
                            dataDirectory
                    );

                    fileOperations.moveAtomically(
                            backupDirectory,
                            dataDirectory
                    );
                }
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }

            throw failure;
        }

        try {
            fileOperations.deleteDirectoryIfExists(
                    backupDirectory
            );
        } catch (IOException cleanupFailure) {
            throw new StorageException(
                    "Workspace was committed, but its backup "
                            + "could not be deleted: "
                            + backupDirectory,
                    cleanupFailure
            );
        }
    }

    void restoreRepositories(
            WorkspaceSnapshot snapshot,
            ProjectRepository projectRepository,
            TaskRepository taskRepository
    ) {
        Objects.requireNonNull(snapshot, "Workspace snapshot must not be null");
        Objects.requireNonNull(projectRepository, "Project repository must not be null");
        Objects.requireNonNull(taskRepository, "Task repository must not be null");

        taskRepository.findAll().stream()
                .map(Task::getId)
                .toList()
                .forEach(taskRepository::deleteById);

        projectRepository.findAll().stream()
                .map(Project::getId)
                .toList()
                .forEach(projectRepository::deleteById);

        Map<UUID, Task> restoredTasks = new HashMap<>();

        for (List<String> fields : snapshot.projectRows().values()) {
            Project project = projectMapper.fromRecord(snapshotRecord(projectMapper, fields));
            projectRepository.save(project);
        }

        for (List<String> fields : snapshot.taskRows().values()) {
            Task task = taskMapper.fromRecord(snapshotRecord(taskMapper, fields));
            taskRepository.save(task);
            restoredTasks.put(task.getId(), task);
        }

        for (List<List<String>> taskWorklogRows : snapshot.worklogRowsByTaskId().values()) {
            for (List<String> fields : taskWorklogRows) {
                WorklogCsvMapper.TaskWorklog taskWorklog =
                        worklogMapper.fromRecord(snapshotRecord(worklogMapper, fields));
                Task task = restoredTasks.get(taskWorklog.taskId());
                if (task == null) {
                    throw new StorageException(
                            "Snapshot worklog references missing task: " + taskWorklog.taskId()
                    );
                }
                task.restoreWorklog(taskWorklog.worklog());
            }
        }
    }

    private void writeWorkspace(
            Path targetDirectory,
            ProjectRepository projectRepository,
            TaskRepository taskRepository
    ) throws IOException {
        Files.createDirectories(targetDirectory);

        List<Project> projects = projectRepository.findAll();
        List<Task> tasks = taskRepository.findAll();
        Iterable<WorklogCsvMapper.TaskWorklog> worklogs = () -> tasks.stream()
                .flatMap(task -> task.getWorklogs().stream()
                        .map(worklog -> new WorklogCsvMapper.TaskWorklog(task.getId(), worklog)))
                .iterator();

        writeAll(targetDirectory, projectMapper, projects);
        writeAll(targetDirectory, taskMapper, tasks);
        writeAll(targetDirectory, worklogMapper, worklogs);
    }

    private void validateWorkspace(Path workspaceDirectory) {
        loadWorkspaceFrom(workspaceDirectory);
    }

    private Path siblingDirectory(String purpose) {
        Path parent = dataDirectory.getParent();

        if (parent == null) {
            throw new StorageException("Workspace directory must have a parent: " + dataDirectory);
        }

        String workspaceName =
                dataDirectory.getFileName().toString();

        return parent.resolve(
                workspaceName + "." + purpose + "-" + UUID.randomUUID()
        );
    }

    private <T> void readAll(
            Path sourceDirectory,
            CsvEntityMapper<T> mapper,
            Consumer<T> consumer
    ) {
        Path path = sourceDirectory.resolve(mapper.fileName());
        if (!Files.exists(path)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            reader.mark(1);
            if (reader.read() != UTF8_BOM.charAt(0)) {
                reader.reset();
            }
            List<List<String>> rows = CsvUtils.parse(reader);
            if (!rows.isEmpty()) {
                validateHeader(mapper, rows.get(0));
            }
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (isBlankRow(row)) {
                    continue;
                }
                CsvRecord record = new CsvRecord(mapper.fileName(), mapper.columns(), row, i + 1);
                consumer.accept(mapper.fromRecord(record));
            }
        } catch (Exception e) {
            throw new StorageException("Failed to load " + mapper.fileName() + ": " + e.getMessage(), e);
        }
    }
    
    private <T> void validateHeader(
        CsvEntityMapper<T> mapper,
        List<String> actualHeader
    ) {
        List<String> normalizedHeader =
                new java.util.ArrayList<>(actualHeader);

        for (int i = 0; i < normalizedHeader.size(); i++) {
            String column = normalizedHeader.get(i).trim();

            if (i == 0 && column.startsWith(UTF8_BOM)) {
                column = column
                        .substring(UTF8_BOM.length())
                        .trim();
            }

            normalizedHeader.set(i, column);
        }

        if (!normalizedHeader.equals(mapper.columns())) {
            throw new StorageException(
                    "Unsupported header in "
                            + mapper.fileName()
                            + ": expected '"
                            + String.join(",", mapper.columns())
                            + "' but found '"
                            + String.join(",", normalizedHeader)
                            + "'"
            );
        }
    }

    private <T> void writeAll(
            Path targetDirectory,
            CsvEntityMapper<T> mapper,
            Iterable<T> values
    ) throws IOException {
        Path path = targetDirectory.resolve(mapper.fileName());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeLine(writer, mapper.columns());
            for (T value : values) {
                writeLine(writer, mapper.toFields(value));
            }
        }
    }

    private <T> CsvRecord snapshotRecord(CsvEntityMapper<T> mapper, List<String> fields) {
        return new CsvRecord(mapper.fileName(), mapper.columns(), fields, 1);
    }

    private static StorageException asStorageException(String message, Exception cause) {
        if (cause instanceof StorageException storageException) {
            return storageException;
        }
        return new StorageException(message + ": " + cause.getMessage(), cause);
    }

    private static void writeLine(BufferedWriter writer, List<String> fields) throws IOException {
        writer.write(CsvUtils.formatRow(fields));
        writer.write('\n');
    }

    private static boolean isBlankRow(List<String> row) {
        return row.isEmpty() || (row.size() == 1 && row.get(0).isBlank());
    }
}
