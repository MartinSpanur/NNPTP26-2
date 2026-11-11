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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvStorageManagerArchiveTest {

    @TempDir
    Path tempDir;

    @Test
    void exportCreatesZipContainingAllWorkspaceCsvFiles() throws IOException {
        Path workspaceDirectory = tempDir.resolve("workspace");
        Path archive = tempDir.resolve("backup.zip");

        CsvStorageManager storageManager =
                new CsvStorageManager(workspaceDirectory);

        var projectRepository = new InMemoryProjectRepository();
        var taskRepository = new InMemoryTaskRepository();

        ProjectService projectService =
                new ProjectService(projectRepository, taskRepository);

        TaskService taskService =
                new TaskService(taskRepository, projectRepository);

        var project = projectService.createProject(
                "Archive project",
                "Project in backup"
        );

        taskService.createTask(
                project.getId(),
                "Archived task",
                "Task in backup",
                Priority.HIGH,
                90,
                LocalDate.of(2026, 12, 1)
        );

        storageManager.save(projectRepository, taskRepository);
        storageManager.exportArchive(archive);

        assertThat(archive)
                .exists()
                .isRegularFile();

        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            Set<String> entryNames = zipFile.stream()
                    .map(ZipEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(entryNames).containsExactlyInAnyOrder(
                    "projects.csv",
                    "tasks.csv",
                    "worklogs.csv"
            );

            assertThat(zipFile.getEntry("projects.csv")).isNotNull();
            assertThat(zipFile.getEntry("tasks.csv")).isNotNull();
            assertThat(zipFile.getEntry("worklogs.csv")).isNotNull();
        }
    }

    @Test
    void exportAndImportPreserveCompleteWorkspace() {
        Path sourceDirectory = tempDir.resolve("source");
        Path targetDirectory = tempDir.resolve("target");
        Path archive = tempDir.resolve("complete-backup.zip");

        CsvStorageManager sourceStorage =
                new CsvStorageManager(sourceDirectory);

        var sourceProjects = new InMemoryProjectRepository();
        var sourceTasks = new InMemoryTaskRepository();

        ProjectService projectService =
                new ProjectService(sourceProjects, sourceTasks);

        TaskService taskService =
                new TaskService(sourceTasks, sourceProjects);

        var project = projectService.createProject(
                "Imported project",
                "Restored description"
        );

        Task originalTask = taskService.createTask(
                project.getId(),
                "Imported task",
                "Restored task description",
                Priority.CRITICAL,
                120,
                LocalDate.of(2026, 12, 15)
        );

        taskService.addLabel(originalTask.getId(), "backup");
        taskService.addLabel(originalTask.getId(), "important");

        taskService.transitionTask(
                originalTask.getId(),
                TaskState.IN_PROGRESS
        );

        taskService.logWork(
                originalTask.getId(),
                45,
                "Martin",
                "Archive test"
        );

        sourceStorage.save(sourceProjects, sourceTasks);
        sourceStorage.exportArchive(archive);

        CsvStorageManager targetStorage =
                new CsvStorageManager(targetDirectory);

        CsvStorageManager.LoadedWorkspace imported =
                targetStorage.importArchive(archive);

        assertThat(imported.projectRepository().findAll())
                .singleElement()
                .satisfies(restoredProject -> {
                    assertThat(restoredProject.getId())
                            .isEqualTo(project.getId());
                    assertThat(restoredProject.getName())
                            .isEqualTo("Imported project");
                    assertThat(restoredProject.getDescription())
                            .isEqualTo("Restored description");
                });

        assertThat(imported.taskRepository().findAll())
                .singleElement()
                .satisfies(restoredTask -> {
                    assertThat(restoredTask.getId())
                            .isEqualTo(originalTask.getId());
                    assertThat(restoredTask.getProjectId())
                            .isEqualTo(project.getId());
                    assertThat(restoredTask.getTitle())
                            .isEqualTo("Imported task");
                    assertThat(restoredTask.getPriority())
                            .isEqualTo(Priority.CRITICAL);
                    assertThat(restoredTask.getState())
                            .isEqualTo(TaskState.IN_PROGRESS);
                    assertThat(restoredTask.getEstimatedMinutes())
                            .isEqualTo(120);
                    assertThat(restoredTask.getDueDate())
                            .isEqualTo(LocalDate.of(2026, 12, 15));
                    assertThat(restoredTask.getLabels())
                            .containsExactly("backup", "important");
                    assertThat(restoredTask.getWorklogs())
                            .singleElement()
                            .satisfies(worklog -> {
                                assertThat(worklog.durationMinutes())
                                        .isEqualTo(45);
                                assertThat(worklog.author())
                                        .isEqualTo("Martin");
                                assertThat(worklog.description())
                                        .isEqualTo("Archive test");
                            });
                });

        assertThat(targetDirectory.resolve("projects.csv"))
                .isRegularFile();
        assertThat(targetDirectory.resolve("tasks.csv"))
                .isRegularFile();
        assertThat(targetDirectory.resolve("worklogs.csv"))
                .isRegularFile();
    }

    @Test
    void importReplacesExistingWorkspaceFiles() {
        Path sourceDirectory = tempDir.resolve("source");
        Path targetDirectory = tempDir.resolve("target");
        Path archive = tempDir.resolve("replacement.zip");

        CsvStorageManager sourceStorage =
                new CsvStorageManager(sourceDirectory);

        var sourceProjects = new InMemoryProjectRepository();
        var sourceTasks = new InMemoryTaskRepository();

        ProjectService sourceProjectService =
                new ProjectService(sourceProjects, sourceTasks);

        sourceProjectService.createProject(
                "Replacement project",
                ""
        );

        sourceStorage.save(sourceProjects, sourceTasks);
        sourceStorage.exportArchive(archive);

        CsvStorageManager targetStorage =
                new CsvStorageManager(targetDirectory);

        var oldProjects = new InMemoryProjectRepository();
        var oldTasks = new InMemoryTaskRepository();

        ProjectService oldProjectService =
                new ProjectService(oldProjects, oldTasks);

        oldProjectService.createProject("Old project", "");

        targetStorage.save(oldProjects, oldTasks);

        CsvStorageManager.LoadedWorkspace imported =
                targetStorage.importArchive(archive);

        assertThat(imported.projectRepository().findAll())
                .extracting(project -> project.getName())
                .containsExactly("Replacement project")
                .doesNotContain("Old project");

        CsvStorageManager.LoadedWorkspace loadedAgain =
                targetStorage.load();

        assertThat(loadedAgain.projectRepository().findAll())
                .extracting(project -> project.getName())
                .containsExactly("Replacement project")
                .doesNotContain("Old project");
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

    private void addZipEntry(
            ZipOutputStream zip,
            String name,
            String content
    ) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}