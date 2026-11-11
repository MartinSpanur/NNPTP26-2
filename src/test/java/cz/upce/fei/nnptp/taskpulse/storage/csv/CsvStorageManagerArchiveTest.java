package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvStorageManagerArchiveTest {

    @TempDir
    Path tempDir;
    
    @Test
    void exportCreatesZipContainingAllWorkspaceCsvFiles()
            throws IOException {

        WorkspaceFixture workspace = createWorkspace("workspace");
        Path archive = tempDir.resolve("backup.zip");

        workspace.save();
        workspace.storage().exportArchive(archive);

        assertThat(archive).isRegularFile();

        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            assertThat(zipEntryNames(zipFile))
                    .containsExactlyInAnyOrder(
                            "projects.csv",
                            "tasks.csv",
                            "worklogs.csv"
                    );
        }
    }

    @Test
    void exportAndImportPreserveCompleteWorkspace() {
        WorkspaceFixture source = createWorkspace("source");
        Path archive = tempDir.resolve("complete-backup.zip");

        RoundTripFixture original =
                createCompleteWorkspace(source);

        source.save();
        source.storage().exportArchive(archive);

        CsvStorageManager.LoadedWorkspace imported =
                new CsvStorageManager(tempDir.resolve("target"))
                        .importArchive(archive);

        assertImportedProject(imported, original);
        assertImportedTask(imported, original);
        assertImportedFilesExist(tempDir.resolve("target"));
    }

    @Test
    void importReplacesExistingWorkspaceFiles() {
        Path archive = tempDir.resolve("replacement.zip");

        WorkspaceFixture source = createWorkspace("source");
        source.projectService().createProject(
                "Replacement project",
                ""
        );
        source.save();
        source.storage().exportArchive(archive);

        WorkspaceFixture target = createWorkspace("target");
        target.projectService().createProject("Old project", "");
        target.save();

        CsvStorageManager.LoadedWorkspace imported =
                target.storage().importArchive(archive);

        assertProjectNames(imported, "Replacement project");
        assertProjectNames(
                target.storage().load(),
                "Replacement project"
        );
    }

    @Test
    void importRejectsArchiveMissingRequiredCsvFile()
            throws IOException {
        Path archive = tempDir.resolve("incomplete.zip");

        try (
                ZipOutputStream zip =
                        new ZipOutputStream(Files.newOutputStream(archive))
        ) {
            addZipEntry(zip, "projects.csv", "id,name,description\n");

            addZipEntry(zip, "tasks.csv", "id,projectId,title,description,priority,state," + "estimatedMinutes,dueDate,labels\n");

            // worklogs.csv is intentionally missing.
        }

        CsvStorageManager storageManager =
                new CsvStorageManager(tempDir.resolve("workspace"));

        assertThatThrownBy(
                () -> storageManager.importArchive(archive)
        )
                .isInstanceOf(StorageException.class)
                .hasMessageContaining(
                        "missing required file: worklogs.csv"
                );
    }

    @Test
    void importRejectsUnsupportedArchiveEntry()
            throws IOException {
        Path archive = tempDir.resolve("unsupported-entry.zip");

        try (
                ZipOutputStream zip =
                        new ZipOutputStream(Files.newOutputStream(archive))
        ) {
            addZipEntry(zip, "projects.csv", "id,name,description\n");

            addZipEntry(zip, "tasks.csv", "id,projectId,title,description,priority,state," + "estimatedMinutes,dueDate,labels\n");

            addZipEntry(zip, "worklogs.csv", "taskId,timestamp,durationMinutes,author,description\n");

            addZipEntry(zip, "unexpected.txt", "not allowed");
        }

        CsvStorageManager storageManager =
                new CsvStorageManager(tempDir.resolve("workspace"));

        assertThatThrownBy(
                () -> storageManager.importArchive(archive)
        )
                .isInstanceOf(StorageException.class)
                .hasMessageContaining(
                        "Unsupported file in workspace archive"
                );
    }

    @Test
    void failedImportDoesNotReplaceExistingWorkspace()
            throws IOException {
        Path workspaceDirectory = tempDir.resolve("workspace");
        Path invalidArchive = tempDir.resolve("invalid.zip");

        CsvStorageManager storageManager =
                new CsvStorageManager(workspaceDirectory);

        var projects = new InMemoryProjectRepository();
        var tasks = new InMemoryTaskRepository();

        ProjectService projectService =
                new ProjectService(projects, tasks);

        projectService.createProject("Keep this project", "");

        storageManager.save(projects, tasks);

        try (
                ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(invalidArchive))
        ) {
            addZipEntry(zip, "projects.csv", "id,name,description\nnot-a-uuid,Broken,\n");

            addZipEntry(zip, "tasks.csv", "id,projectId,title,description,priority,state," + "estimatedMinutes,dueDate,labels\n");

            addZipEntry(zip, "worklogs.csv", "taskId,timestamp,durationMinutes,author,description\n");
        }

        assertThatThrownBy(
                () -> storageManager.importArchive(invalidArchive)
        ).isInstanceOf(StorageException.class);

        CsvStorageManager.LoadedWorkspace unchanged =
                storageManager.load();

        assertThat(unchanged.projectRepository().findAll())
                .extracting(project -> project.getName())
                .containsExactly("Keep this project");
    }
    
    
    // HELPERS

    private void addZipEntry(
            ZipOutputStream zip,
            String name,
            String content
    ) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    
    private WorkspaceFixture createWorkspace(String directoryName) {
        Path directory = tempDir.resolve(directoryName);

        var projects = new InMemoryProjectRepository();
        var tasks = new InMemoryTaskRepository();

        return new WorkspaceFixture(
                new CsvStorageManager(directory),
                projects,
                tasks,
                new ProjectService(projects, tasks),
                new TaskService(tasks, projects)
        );
    }

    private record WorkspaceFixture(
            CsvStorageManager storage,
            InMemoryProjectRepository projects,
            InMemoryTaskRepository tasks,
            ProjectService projectService,
            TaskService taskService
    ) {
        void save() {
            storage.save(projects, tasks);
        }
    }
    
    private Set<String> zipEntryNames(ZipFile zipFile) {
        return zipFile.stream()
                .map(ZipEntry::getName)
                .collect(java.util.stream.Collectors.toSet());
    }
    
    private RoundTripFixture createCompleteWorkspace(
        WorkspaceFixture workspace
    ) {
        var project = workspace.projectService().createProject(
                "Imported project",
                "Restored description"
        );

        Task task = workspace.taskService().createTask(
                project.getId(),
                "Imported task",
                "Restored task description",
                Priority.CRITICAL,
                120,
                LocalDate.of(2026, 12, 15)
        );

        workspace.taskService().addLabel(task.getId(), "backup");
        workspace.taskService().addLabel(task.getId(), "important");

        workspace.taskService().transitionTask(
                task.getId(),
                TaskState.IN_PROGRESS
        );

        workspace.taskService().logWork(
                task.getId(),
                45,
                "Martin",
                "Archive test"
        );

        return new RoundTripFixture(
                project.getId(),
                task.getId()
        );
    }

    private void assertImportedProject(
            CsvStorageManager.LoadedWorkspace imported,
            RoundTripFixture original
    ) {
        assertThat(imported.projectRepository().findAll())
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.getId())
                            .isEqualTo(original.projectId());
                    assertThat(project.getName())
                            .isEqualTo("Imported project");
                    assertThat(project.getDescription())
                            .isEqualTo("Restored description");
                });
    }

    private void assertImportedTask(
            CsvStorageManager.LoadedWorkspace imported,
            RoundTripFixture original
    ) {
        assertThat(imported.taskRepository().findAll())
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getId())
                            .isEqualTo(original.taskId());
                    assertThat(task.getProjectId())
                            .isEqualTo(original.projectId());
                    assertThat(task.getTitle())
                            .isEqualTo("Imported task");
                    assertThat(task.getPriority())
                            .isEqualTo(Priority.CRITICAL);
                    assertThat(task.getState())
                            .isEqualTo(TaskState.IN_PROGRESS);
                    assertThat(task.getEstimatedMinutes())
                            .isEqualTo(120);
                    assertThat(task.getDueDate())
                            .isEqualTo(LocalDate.of(2026, 12, 15));
                    assertThat(task.getLabels())
                            .containsExactly("backup", "important");

                    assertImportedWorklog(task);
                });
    }

    private void assertImportedWorklog(Task task) {
        assertThat(task.getWorklogs())
                .singleElement()
                .satisfies(worklog -> {
                    assertThat(worklog.durationMinutes())
                            .isEqualTo(45);
                    assertThat(worklog.author())
                            .isEqualTo("Martin");
                    assertThat(worklog.description())
                            .isEqualTo("Archive test");
                });
    }

    private void assertImportedFilesExist(Path directory) {
        assertThat(directory.resolve("projects.csv"))
                .isRegularFile();
        assertThat(directory.resolve("tasks.csv"))
                .isRegularFile();
        assertThat(directory.resolve("worklogs.csv"))
                .isRegularFile();
    }

    private record RoundTripFixture(
            UUID projectId,
            UUID taskId
    ) {
    }
    
    private void assertProjectNames(
        CsvStorageManager.LoadedWorkspace workspace,
        String... expectedNames
    ) {
        assertThat(workspace.projectRepository().findAll())
                .extracting(project -> project.getName())
                .containsExactlyInAnyOrder(expectedNames);
    }
}