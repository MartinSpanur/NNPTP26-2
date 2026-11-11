package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvStorageManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripWithQuotesCommasNewlinesAndUnicode() {
        CsvStorageManager storage = new CsvStorageManager(tempDir);
        InMemoryProjectRepository projectRepo = new InMemoryProjectRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        Project project = new Project(UUID.randomUUID(), "Proč, \"ano\"?", "Projekt s čárkou, \"uvozovkami\"\na novým řádkem");
        projectRepo.save(project);

        Task task = new Task(UUID.randomUUID(), project.getId(), "Úkol s čárkou, \"uvozovkami\"",
                "Popis\ns řádky\na znaky: Příliš žluťoučký kůň", Priority.HIGH, TaskState.IN_PROGRESS,
                150, LocalDate.of(2026, 12, 31), List.of("backend", "čestina"), null);
        task.addWorklog(new Worklog(LocalDateTime.of(2026, 10, 21, 14, 30), 45, "Petr Novák", "Práce, testy \"hotovo\""));
        taskRepo.save(task);

        storage.save(projectRepo, taskRepo);

        CsvStorageManager.LoadedWorkspace loaded = storage.load();

        assertThat(loaded.projectRepository().findAll()).hasSize(1);
        Project loadedProject = loaded.projectRepository().findById(project.getId()).orElseThrow();
        assertThat(loadedProject.getName()).isEqualTo(project.getName());
        assertThat(loadedProject.getDescription()).isEqualTo(project.getDescription());

        assertThat(loaded.taskRepository().findAll()).hasSize(1);
        Task loadedTask = loaded.taskRepository().findById(task.getId()).orElseThrow();
        assertThat(loadedTask.getTitle()).isEqualTo(task.getTitle());
        assertThat(loadedTask.getDescription()).isEqualTo(task.getDescription());
        assertThat(loadedTask.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(loadedTask.getState()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(loadedTask.getEstimatedMinutes()).isEqualTo(150);
        assertThat(loadedTask.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(loadedTask.getLabels()).containsExactlyInAnyOrder("backend", "čestina");

        assertThat(loadedTask.getWorklogs()).hasSize(1);
        Worklog loadedWorklog = loadedTask.getWorklogs().get(0);
        assertThat(loadedWorklog.durationMinutes()).isEqualTo(45L);
        assertThat(loadedWorklog.author()).isEqualTo("Petr Novák");
        assertThat(loadedWorklog.description()).isEqualTo("Práce, testy \"hotovo\"");
    }

    @Test
    void loadFromNonExistentDirectoryInitializesEmptyWorkspace() {
        Path nonExistent = tempDir.resolve("does_not_exist");
        CsvStorageManager storage = new CsvStorageManager(nonExistent);

        CsvStorageManager.LoadedWorkspace workspace = storage.load();
        assertThat(workspace.projectRepository().findAll()).isEmpty();
        assertThat(workspace.taskRepository().findAll()).isEmpty();
    }

    @Test
    void rejectDuplicateProjectIdInCsv() throws IOException {
        UUID id = UUID.randomUUID();
        String csvContent = "id,name,description\n" +
                id + ",Project 1,Desc 1\n" +
                id + ",Project 2,Desc 2\n";
        Files.writeString(tempDir.resolve("projects.csv"), csvContent);

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(storage::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Duplicate project id");
    }

    @Test
    void rejectTaskReferencingMissingProject() throws IOException {
        String projectCsv = "id,name,description\n";
        String taskCsv = "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n" +
                UUID.randomUUID() + "," + UUID.randomUUID() + ",Title,Desc,MEDIUM,TODO,,,\n";

        Files.writeString(tempDir.resolve("projects.csv"), projectCsv);
        Files.writeString(tempDir.resolve("tasks.csv"), taskCsv);

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(storage::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("missing project");
    }
    
    @ParameterizedTest
    @ValueSource(strings = {";", ";;", " ; ", "backend;;frontend", "backend;", ";backend", "   "})
    void rejectEmptyLabelEdgeCasesInCsv(String invalidLabels) throws IOException {
        UUID projectId = UUID.randomUUID();
        String projectCsv = "id,name,description\n" +
                projectId + ",Project 1,Desc 1\n";
        String taskCsv = "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n" +
                UUID.randomUUID() + "," + projectId + ",Title,Desc,MEDIUM,TODO,,," + invalidLabels + "\n";

        Files.writeString(tempDir.resolve("projects.csv"), projectCsv);
        Files.writeString(tempDir.resolve("tasks.csv"), taskCsv);

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(storage::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Label must not be blank");
    }
    
    @Test
    void loadTaskWithEmptyLabelsFieldSuccessfully() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        String projectCsv = "id,name,description\n" +
                projectId + ",Project 1,Desc 1\n";
        String taskCsv = "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n" +
                taskId + "," + projectId + ",Title,Desc,MEDIUM,TODO,,,\n";

        Files.writeString(tempDir.resolve("projects.csv"), projectCsv);
        Files.writeString(tempDir.resolve("tasks.csv"), taskCsv);

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        CsvStorageManager.LoadedWorkspace workspace = storage.load();

        Task loadedTask = workspace.taskRepository().findById(taskId).orElseThrow();
        assertThat(loadedTask.getLabels()).isEmpty();
    }

    @Test
    void malformedDueDateReportsLineAndColumnAndToken() throws IOException {
        UUID projectId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + projectId + ",Project,\n");
        String taskCsv = "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n" +
                UUID.randomUUID() + "," + projectId + ",Title,Desc,MEDIUM,TODO,,31-02-2026,\n";
        Files.writeString(tempDir.resolve("tasks.csv"), taskCsv);

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(storage::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Malformed value in tasks.csv")
                .hasMessageContaining("line 2")
                .hasMessageContaining("column 'dueDate'")
                .hasMessageContaining("31-02-2026");
    }

    @Test
    void malformedEstimatedMinutesReportsLineAndColumnAndToken() throws IOException {
        UUID projectId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + projectId + ",Project,\n");
        String taskCsv = "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n" +
                UUID.randomUUID() + "," + projectId + ",Title,Desc,MEDIUM,TODO,abc,,\n";
        Files.writeString(tempDir.resolve("tasks.csv"), taskCsv);

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(storage::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("column 'estimatedMinutes'")
                .hasMessageContaining("abc");
    }

    @Test
    void malformedTimestampInWorklogReportsLineAndColumnAndToken() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + projectId + ",Project,\n");
        Files.writeString(tempDir.resolve("tasks.csv"),
                "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n" +
                        taskId + "," + projectId + ",Title,Desc,MEDIUM,IN_PROGRESS,,,\n");
        Files.writeString(tempDir.resolve("worklogs.csv"),
                "taskId,timestamp,durationMinutes,author,description\n" +
                        taskId + ",not-a-timestamp,45,Author,\n");

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(storage::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("column 'timestamp'")
                .hasMessageContaining("not-a-timestamp");
    }

    @Test
    void malformedDurationInWorklogReportsLineAndColumnAndToken() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + projectId + ",Project,\n");
        Files.writeString(tempDir.resolve("tasks.csv"),
                "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n" +
                        taskId + "," + projectId + ",Title,Desc,MEDIUM,IN_PROGRESS,,,\n");
        Files.writeString(tempDir.resolve("worklogs.csv"),
                "taskId,timestamp,durationMinutes,author,description\n" +
                        taskId + ",2026-10-21T14:30:00,NaN,Author,\n");

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(storage::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("column 'durationMinutes'")
                .hasMessageContaining("NaN");
    }

    @Test
    void malformedPriorityReportsLineAndColumnAndToken() throws IOException {
        UUID projectId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + projectId + ",Project,\n");
        String taskCsv = "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n" +
                UUID.randomUUID() + "," + projectId + ",Title,Desc,URGENT,TODO,,,\n";
        Files.writeString(tempDir.resolve("tasks.csv"), taskCsv);

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(storage::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("column 'priority'")
                .hasMessageContaining("URGENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\n\n", "\n\n\n", "\r\n\r\n", "\r\r", "\n   \n", "\n\t\n"})
    void ignoreTrailingEmptyLinesInAllCsvFiles(String trailingLines) throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + projectId + ",Project,Desc" + trailingLines);
        Files.writeString(tempDir.resolve("tasks.csv"),
                "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n"
                        + taskId + "," + projectId + ",Title,Desc,MEDIUM,IN_PROGRESS,,," + trailingLines);
        Files.writeString(tempDir.resolve("worklogs.csv"),
                "taskId,timestamp,durationMinutes,author,description\n"
                        + taskId + ",2026-10-21T14:30,45,Author,Work" + trailingLines);

        CsvStorageManager.LoadedWorkspace workspace = new CsvStorageManager(tempDir).load();

        assertThat(workspace.projectRepository().findAll()).hasSize(1);
        assertThat(workspace.taskRepository().findAll()).hasSize(1);
        assertThat(workspace.taskRepository().findById(taskId).orElseThrow().getWorklogs()).hasSize(1);
    }

    @Test
    void ignoreTrailingEmptyLinesInCrlfCsvFile() throws IOException {
        UUID projectId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\r\n" + projectId + ",Project,Desc\r\n\r\n\r\n");

        CsvStorageManager.LoadedWorkspace workspace = new CsvStorageManager(tempDir).load();

        assertThat(workspace.projectRepository().findAll())
                .extracting(Project::getId)
                .containsExactly(projectId);
    }

    @Test
    void rejectDelimiterOnlyRowInProjectsCsv() throws IOException {
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + UUID.randomUUID() + ",Project,Desc\n,,\n");

        assertThatThrownBy(new CsvStorageManager(tempDir)::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Empty record in projects.csv at line 3")
                .hasMessageContaining("only delimiters");
    }

    @Test
    void rejectDelimiterOnlyRowInTasksCsv() throws IOException {
        UUID projectId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + projectId + ",Project,Desc\n");
        Files.writeString(tempDir.resolve("tasks.csv"),
                "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n"
                        + ",,,,,,,,\n");

        assertThatThrownBy(new CsvStorageManager(tempDir)::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Empty record in tasks.csv at line 2")
                .hasMessageContaining("only delimiters");
    }

    @Test
    void rejectDelimiterOnlyRowWithWhitespaceInWorklogsCsv() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + projectId + ",Project,Desc\n");
        Files.writeString(tempDir.resolve("tasks.csv"),
                "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n"
                        + taskId + "," + projectId + ",Title,Desc,MEDIUM,IN_PROGRESS,,,\n");
        Files.writeString(tempDir.resolve("worklogs.csv"),
                "taskId,timestamp,durationMinutes,author,description\n"
                        + taskId + ",2026-10-21T14:30,45,Author,Work\n"
                        + " , ,  , ,\n");

        assertThatThrownBy(new CsvStorageManager(tempDir)::load)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Empty record in worklogs.csv at line 3")
                .hasMessageContaining("only delimiters");
    }
}
