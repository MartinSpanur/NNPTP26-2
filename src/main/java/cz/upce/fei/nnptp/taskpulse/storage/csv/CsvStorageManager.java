package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.*;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CsvStorageManager {
    private final Path dataDirectory;
    
    private static final List<String> WORKSPACE_FILES = List.of(
        "projects.csv",
        "tasks.csv",
        "worklogs.csv"
    );

    public CsvStorageManager(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "Data directory must not be null");
    }

    public record LoadedWorkspace(ProjectRepository projectRepository, TaskRepository taskRepository) {}

    @FunctionalInterface
    private interface FieldParser<T> {
        T parse(String raw);
    }

    private <T> T parseField(String file, String columnName, int lineNumber, String raw, FieldParser<T> parser) {
        try {
            return parser.parse(raw.strip());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new StorageException("Malformed value in " + file + " at line " + lineNumber
                    + ", column '" + columnName + "': invalid token '" + raw + "'", e);
        }
    }

    public LoadedWorkspace load() {
        InMemoryProjectRepository projectRepo = new InMemoryProjectRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        if (!Files.exists(dataDirectory)) {
            return new LoadedWorkspace(projectRepo, taskRepo);
        }

        Path projectsPath = dataDirectory.resolve("projects.csv");
        Path tasksPath = dataDirectory.resolve("tasks.csv");
        Path worklogsPath = dataDirectory.resolve("worklogs.csv");

        Set<UUID> projectIds = new HashSet<>();
        if (Files.exists(projectsPath)) {
            loadProjects(projectsPath, projectRepo, projectIds);
        }

        Map<UUID, Task> taskMap = new HashMap<>();
        if (Files.exists(tasksPath)) {
            loadTasks(tasksPath, taskRepo, projectIds, taskMap);
        }

        if (Files.exists(worklogsPath)) {
            loadWorklogs(worklogsPath, taskMap);
        }

        return new LoadedWorkspace(projectRepo, taskRepo);
    }

    private void loadProjects(Path path, InMemoryProjectRepository projectRepo, Set<UUID> projectIds) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<List<String>> rows = CsvUtils.parse(reader);
            if (rows.isEmpty()) return;
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.isEmpty() || (row.size() == 1 && row.get(0).isBlank())) continue;
                if (row.size() < 3) {
                    throw new StorageException("Malformed row in projects.csv at line " + (i + 1));
                }
                UUID id = parseField("projects.csv", "id", i + 1, row.get(0), UUID::fromString);
                if (!projectIds.add(id)) {
                    throw new StorageException("Duplicate project id detected: " + id);
                }
                String name = row.get(1);
                String description = row.get(2);
                projectRepo.save(new Project(id, name, description));
            }
        } catch (Exception e) {
            throw new StorageException("Failed to load projects from CSV: " + e.getMessage(), e);
        }
    }

    private void loadTasks(Path path, InMemoryTaskRepository taskRepo, Set<UUID> projectIds, Map<UUID, Task> taskMap) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<List<String>> rows = CsvUtils.parse(reader);
            if (rows.isEmpty()) return;
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.isEmpty() || (row.size() == 1 && row.get(0).isBlank())) continue;
                if (row.size() < 9) {
                    throw new StorageException("Malformed row in tasks.csv at line " + (i + 1));
                }
                UUID id = parseField("tasks.csv", "id", i + 1, row.get(0), UUID::fromString);
                UUID projectId = parseField("tasks.csv", "projectId", i + 1, row.get(1), UUID::fromString);
                if (!projectIds.contains(projectId)) {
                    throw new StorageException("Task references missing project: " + projectId);
                }
                if (taskMap.containsKey(id)) {
                    throw new StorageException("Duplicate task id detected: " + id);
                }
                String title = row.get(2);
                String description = row.get(3);
                Priority priority = parseField("tasks.csv", "priority", i + 1, row.get(4), Priority::valueOf);
                TaskState state = parseField("tasks.csv", "state", i + 1, row.get(5), TaskState::valueOf);
                Integer estimatedMinutes = row.get(6).isBlank()
                        ? null
                        : parseField("tasks.csv", "estimatedMinutes", i + 1, row.get(6), Integer::valueOf);
                LocalDate dueDate = row.get(7).isBlank()
                        ? null
                        : parseField("tasks.csv", "dueDate", i + 1, row.get(7), LocalDate::parse);
                String rawLabels = row.get(8);
                List<String> labels = new ArrayList<>();
                if (!rawLabels.isEmpty()) {
                    labels = Arrays.asList(rawLabels.split(";", -1));
                }

                Task task = new TaskBuilder(projectId, title)
                        .id(id)
                        .description(description)
                        .priority(priority)
                        .state(state)
                        .estimatedMinutes(estimatedMinutes)
                        .dueDate(dueDate)
                        .labels(labels)
                        .build();

                taskRepo.save(task);
                taskMap.put(id, task);
            }
        } catch (Exception e) {
            throw new StorageException("Failed to load tasks from CSV: " + e.getMessage(), e);
        }
    }

    private void loadWorklogs(Path path, Map<UUID, Task> taskMap) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<List<String>> rows = CsvUtils.parse(reader);
            if (rows.isEmpty()) return;
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.isEmpty() || (row.size() == 1 && row.get(0).isBlank())) continue;
                if (row.size() < 5) {
                    throw new StorageException("Malformed row in worklogs.csv at line " + (i + 1));
                }
                UUID taskId = parseField("worklogs.csv", "taskId", i + 1, row.get(0), UUID::fromString);
                Task task = taskMap.get(taskId);
                if (task == null) {
                    throw new StorageException("Worklog references missing task: " + taskId);
                }
                LocalDateTime timestamp = parseField("worklogs.csv", "timestamp", i + 1, row.get(1), LocalDateTime::parse);
                long durationMinutes = parseField("worklogs.csv", "durationMinutes", i + 1, row.get(2), Long::parseLong);
                String author = row.get(3);
                String description = row.get(4);
                Worklog worklog = new Worklog(timestamp, durationMinutes, author, description);
                task.restoreWorklog(worklog);
            }
        } catch (Exception e) {
            throw new StorageException("Failed to load worklogs from CSV: " + e.getMessage(), e);
        }
    }

    public void save(ProjectRepository projectRepository, TaskRepository taskRepository) {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            saveProjects(dataDirectory.resolve("projects.csv"), projectRepository.findAll());
            List<Task> tasks = taskRepository.findAll();
            saveTasks(dataDirectory.resolve("tasks.csv"), tasks);
            saveWorklogs(dataDirectory.resolve("worklogs.csv"), tasks);
        } catch (IOException e) {
            throw new StorageException("Failed to save workspace to CSV: " + e.getMessage(), e);
        }
    }
    
    public void exportArchive(Path archivePath) {
        Objects.requireNonNull(
                archivePath,
                "Archive path must not be null"
        );

        Path destination = archivePath.toAbsolutePath().normalize();

        try {
            validateExportSource();
            createParentDirectory(destination);

            Path temporaryArchive = createTemporaryArchive(destination);

            try {
                writeArchive(temporaryArchive);
                moveReplacing(temporaryArchive, destination);
            } finally {
                Files.deleteIfExists(temporaryArchive);
            }
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException(
                    "Failed to export workspace archive: " + e.getMessage(),
                    e
            );
        }
    }
    
    public LoadedWorkspace importArchive(Path archivePath) {
        Objects.requireNonNull(
                archivePath,
                "Archive path must not be null"
        );

        Path source = archivePath.toAbsolutePath().normalize();
        validateArchiveExists(source);

        Path temporaryDirectory = null;

        try {
            temporaryDirectory =
                    Files.createTempDirectory("taskpulse-import-");

            prepareImportedWorkspace(source, temporaryDirectory);
            installImportedFiles(temporaryDirectory);

            return load();
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException(
                    "Failed to import workspace archive: " + e.getMessage(),
                    e
            );
        } finally {
            if (temporaryDirectory != null) {
                deleteDirectoryQuietly(temporaryDirectory);
            }
        }
    }
    
    private void validateImportedFiles(Path directory) {
        for (String fileName : WORKSPACE_FILES) {
            Path file = directory.resolve(fileName);

            if (!Files.isRegularFile(file)) {
                throw new StorageException(
                        "Workspace archive is missing required file: "
                                + fileName
                );
            }
        }
    }

    private void moveReplacing(
            Path source,
            Path destination
    ) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted((first, second) -> second.compareTo(first))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Cleanup failure must not hide the original result.
                        }
                    });
        } catch (IOException ignored) {
            // Cleanup failure must not hide the original result.
        }
    }

    private void saveProjects(Path path, List<Project> projects) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("id,name,description\n");
            for (Project p : projects) {
                writer.write(p.getId().toString() + "," +
                        CsvUtils.escape(p.getName()) + "," +
                        CsvUtils.escape(p.getDescription()) + "\n");
            }
        }
    }

    private void saveTasks(Path path, List<Task> tasks) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n");
            for (Task t : tasks) {
                String labels = String.join(";", t.getLabels());
                writer.write(t.getId().toString() + "," +
                        t.getProjectId().toString() + "," +
                        CsvUtils.escape(t.getTitle()) + "," +
                        CsvUtils.escape(t.getDescription()) + "," +
                        t.getPriority().name() + "," +
                        t.getState().name() + "," +
                        (t.getEstimatedMinutes() == null ? "" : t.getEstimatedMinutes().toString()) + "," +
                        (t.getDueDate() == null ? "" : t.getDueDate().toString()) + "," +
                        CsvUtils.escape(labels) + "\n");
            }
        }
    }

    private void saveWorklogs(Path path, List<Task> tasks) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("taskId,timestamp,durationMinutes,author,description\n");
            for (Task t : tasks) {
                for (Worklog w : t.getWorklogs()) {
                    writer.write(t.getId().toString() + "," +
                            w.timestamp().toString() + "," +
                            w.durationMinutes() + "," +
                            CsvUtils.escape(w.author()) + "," +
                            CsvUtils.escape(w.description()) + "\n");
                }
            }
        }
    }
    
    
    
    private void validateExportSource() {
        if (!Files.isDirectory(dataDirectory)) {
            throw new StorageException(
                    "Workspace data directory does not exist: "
                            + dataDirectory.toAbsolutePath()
            );
        }
    }

    private void createParentDirectory(Path path) throws IOException {
        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private Path createTemporaryArchive(Path destination)
            throws IOException {

        return Files.createTempFile(
                destination.getParent(),
                "taskpulse-export-",
                ".zip"
        );
    }

    private void writeArchive(Path archivePath) throws IOException {
        try (
                OutputStream fileOutput =
                        Files.newOutputStream(archivePath);
                BufferedOutputStream bufferedOutput =
                        new BufferedOutputStream(fileOutput);
                ZipOutputStream zipOutput =
                        new ZipOutputStream(bufferedOutput)
        ) {
            for (String fileName : WORKSPACE_FILES) {
                addWorkspaceFileToArchive(zipOutput, fileName);
            }
        }
    }

    private void addWorkspaceFileToArchive(
            ZipOutputStream zipOutput,
            String fileName
    ) throws IOException {

        Path source = dataDirectory.resolve(fileName);

        if (!Files.isRegularFile(source)) {
            throw new StorageException(
                    "Workspace file is missing: " + fileName
            );
        }

        zipOutput.putNextEntry(new ZipEntry(fileName));

        try (
                InputStream input = new BufferedInputStream(
                        Files.newInputStream(source)
                )
        ) {
            input.transferTo(zipOutput);
        } finally {
            zipOutput.closeEntry();
        }
    }
    
    
    
    private void validateArchiveExists(Path archivePath) {
        if (!Files.isRegularFile(archivePath)) {
            throw new StorageException(
                    "Workspace archive does not exist: " + archivePath
            );
        }
    }

    private void prepareImportedWorkspace(
            Path archivePath,
            Path temporaryDirectory
    ) throws IOException {

        extractArchive(archivePath, temporaryDirectory);
        validateImportedFiles(temporaryDirectory);
        validateImportedWorkspace(temporaryDirectory);
    }

    private void validateImportedWorkspace(Path directory) {
        CsvStorageManager importedStorage =
                new CsvStorageManager(directory);

        // Parse all files and validate cross-file references before
        // replacing the current workspace.
        importedStorage.load();
    }

    private void installImportedFiles(Path sourceDirectory)
            throws IOException {

        Files.createDirectories(dataDirectory);

        for (String fileName : WORKSPACE_FILES) {
            Files.copy(
                    sourceDirectory.resolve(fileName),
                    dataDirectory.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    
    
    private void extractArchive(
        Path archivePath,
        Path targetDirectory
    ) throws IOException {

        Set<String> extractedFiles = new HashSet<>();

        try (
                InputStream fileInput =
                        Files.newInputStream(archivePath);
                BufferedInputStream bufferedInput =
                        new BufferedInputStream(fileInput);
                ZipInputStream zipInput =
                        new ZipInputStream(bufferedInput)
        ) {
            ZipEntry entry;

            while ((entry = zipInput.getNextEntry()) != null) {
                try {
                    extractEntry(
                            zipInput,
                            entry,
                            targetDirectory,
                            extractedFiles
                    );
                } finally {
                    zipInput.closeEntry();
                }
            }
        }
    }
    
    private void extractEntry(
        ZipInputStream zipInput,
        ZipEntry entry,
        Path targetDirectory,
        Set<String> extractedFiles
    ) throws IOException {

        if (entry.isDirectory()) {
            return;
        }

        String entryName = normalizeEntryName(entry);
        validateEntryName(entry, entryName);
        registerEntry(extractedFiles, entryName);

        Path outputPath =
                resolveSafeOutputPath(targetDirectory, entryName);

        try (
                OutputStream output = new BufferedOutputStream(
                        Files.newOutputStream(outputPath)
                )
        ) {
            zipInput.transferTo(output);
        }
    }

    private String normalizeEntryName(ZipEntry entry) {
        return entry.getName().replace('\\', '/');
    }

    private void validateEntryName(
            ZipEntry entry,
            String entryName
    ) {
        if (entryName.contains("/")
                || !WORKSPACE_FILES.contains(entryName)) {

            throw new StorageException(
                    "Unsupported file in workspace archive: "
                            + entry.getName()
            );
        }
    }

    private void registerEntry(
            Set<String> extractedFiles,
            String entryName
    ) {
        if (!extractedFiles.add(entryName)) {
            throw new StorageException(
                    "Duplicate file in workspace archive: " + entryName
            );
        }
    }

    private Path resolveSafeOutputPath(
            Path targetDirectory,
            String entryName
    ) {
        Path normalizedTarget =
                targetDirectory.toAbsolutePath().normalize();

        Path outputPath =
                normalizedTarget.resolve(entryName).normalize();

        if (!outputPath.startsWith(normalizedTarget)) {
            throw new StorageException(
                    "Unsafe archive entry: " + entryName
            );
        }

        return outputPath;
    }
}
