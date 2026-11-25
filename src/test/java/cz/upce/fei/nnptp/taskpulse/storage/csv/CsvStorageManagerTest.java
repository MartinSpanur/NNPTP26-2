package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Label;
import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.IWorkspaceUnitOfWork;
import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;
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
import static org.assertj.core.api.Assertions.assertThatCode;
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

        save(storage, projectRepo, taskRepo);

        LoadedWorkspace loaded = load(storage);

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
    void failedSaveShouldPreserveExistingCsvContent() throws IOException {
        CsvStorageManager storage = new CsvStorageManager(tempDir);
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        InMemoryProjectRepository originalRepo = new InMemoryProjectRepository();
        originalRepo.save(new Project(UUID.randomUUID(), "Original", "Original data"));

        save(storage, originalRepo, taskRepo);

        Path projectsFile = tempDir.resolve("projects.csv");
        byte[] originalContent = Files.readAllBytes(projectsFile);

        InMemoryProjectRepository failingRepo = new InMemoryProjectRepository() {
            @Override
            public List<Project> findAll() {
                return java.util.Arrays.asList(
                    new Project(UUID.randomUUID(), "Updated", "New data"),
                    null
                );
            }
        };

        assertThatThrownBy(() -> save(storage, failingRepo, taskRepo))
            .isInstanceOf(NullPointerException.class);

        assertThat(Files.readAllBytes(projectsFile))
            .isEqualTo(originalContent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Co\"ur\"se", "\"Course\"oops"})
    void rejectsMalformedQuotesWithoutChangingSourceFile(String name) throws IOException {
        Path source = tempDir.resolve("projects.csv");
        Files.writeString(source, "id,name,description\n" + UUID.randomUUID() + "," + name + ",Description\n");
        byte[] originalContent = Files.readAllBytes(source);
        CsvStorageManager storage = new CsvStorageManager(tempDir);

        assertThatThrownBy(() -> load(storage))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to load projects.csv", "Invalid CSV");
        assertThat(Files.readAllBytes(source)).isEqualTo(originalContent);
    }

    @Test
    void loadFromNonExistentDirectoryInitializesEmptyWorkspace() {
        Path nonExistent = tempDir.resolve("does_not_exist");
        CsvStorageManager storage = new CsvStorageManager(nonExistent);

        LoadedWorkspace workspace = load(storage);
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
        assertThatThrownBy(() -> load(storage))
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
        assertThatThrownBy(() -> load(storage))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("missing project");
    }

    @Test
    void rejectDuplicateTaskIdInCsv() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + projectId + ",Project,\n");
        Files.writeString(tempDir.resolve("tasks.csv"),
                "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n" +
                taskId + "," + projectId + ",First,,MEDIUM,TODO,,,\n" +
                taskId + "," + projectId + ",Second,,MEDIUM,TODO,,,\n");

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(() -> load(storage))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Duplicate task id");
    }

    @Test
    void rejectWorklogReferencingMissingTask() throws IOException {
        UUID missingTaskId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("worklogs.csv"),
                "taskId,timestamp,durationMinutes,author,description\n" +
                missingTaskId + ",2026-10-21T14:30,30,Petr,\n");

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(() -> load(storage))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("missing task")
                .hasMessageContaining(missingTaskId.toString());
    }

    @Test
    void skipBlankLinesBetweenRows() throws IOException {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + first + ",First,\n\n" + second + ",Second,\n");

        LoadedWorkspace workspace = load(new CsvStorageManager(tempDir));

        assertThat(workspace.projectRepository().findAll())
                .extracting(Project::getId)
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void rejectRowWithMissingColumns() throws IOException {
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + UUID.randomUUID() + ",Only name\n");

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(() -> load(storage))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Malformed row in projects.csv at line 2");
    }

    @Test
    void rejectReorderedHeaderInsteadOfMappingColumnsByPosition() throws IOException {
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,description,name\n" + UUID.randomUUID() + ",Some description,Name\n");

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(() -> load(storage))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Unsupported header in projects.csv")
                .hasMessageContaining("expected 'id,name,description'")
                .hasMessageContaining("found 'id,description,name'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"projects.csv", "tasks.csv", "worklogs.csv"})
    void rejectUnknownHeaderInEveryCsvFile(String fileName) throws IOException {
        Files.writeString(tempDir.resolve(fileName), "foo,bar\n");

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(() -> load(storage))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Unsupported header in " + fileName);
    }

    @Test
    void rejectHeaderWithMissingColumn() throws IOException {
        Files.writeString(tempDir.resolve("worklogs.csv"), "taskId,timestamp,durationMinutes,author\n");

        CsvStorageManager storage = new CsvStorageManager(tempDir);
        assertThatThrownBy(() -> load(storage))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Unsupported header in worklogs.csv");
    }

    @Test
    void acceptHeaderWithSurroundingWhitespace() throws IOException {
        UUID id = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                " id , name , description \n" + id + ",Name,Description\n");

        LoadedWorkspace workspace = load(new CsvStorageManager(tempDir));

        assertThat(workspace.projectRepository().findById(id)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "\"id\""})
    void acceptHeaderStartingWithUtf8ByteOrderMark(String firstColumn) throws IOException {
        UUID id = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "\uFEFF" + firstColumn + ",name,description\n" + id + ",Name,Description\n");

        LoadedWorkspace workspace = load(new CsvStorageManager(tempDir));

        assertThat(workspace.projectRepository().findById(id)).isPresent();
    }

    @Test
    void acceptEmptyCsvFileWithoutHeader() throws IOException {
        Files.writeString(tempDir.resolve("projects.csv"), "");

        LoadedWorkspace workspace = load(new CsvStorageManager(tempDir));

        assertThat(workspace.projectRepository().findAll()).isEmpty();
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
        assertThatThrownBy(() -> load(storage))
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
        LoadedWorkspace workspace = load(storage);

        Task loadedTask = workspace.taskRepository().findById(taskId).orElseThrow();
        assertThat(loadedTask.getLabels()).isEmpty();
    }

    @Test
    void labelContainingSeparatorNeverReachesWorkspaceFile() {
        CsvStorageManager storage = new CsvStorageManager(tempDir);
        InMemoryProjectRepository projectRepo = new InMemoryProjectRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        Project project = new Project("Projekt", "Popis");
        projectRepo.save(project);
        Task task = new Task(project.getId(), "Úkol");
        task.addLabel("urgent");
        taskRepo.save(task);

        assertThatThrownBy(() -> task.addLabel("release" + Label.SEPARATOR))
                .isInstanceOf(ValidationException.class);

        save(storage, projectRepo, taskRepo);

        assertThatCode(() -> load(storage)).doesNotThrowAnyException();
        Task reloaded = load(storage).taskRepository().findById(task.getId()).orElseThrow();
        assertThat(reloaded.getLabels()).containsExactly("urgent");
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
        assertThatThrownBy(() -> load(storage))
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
        assertThatThrownBy(() -> load(storage))
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
        assertThatThrownBy(() -> load(storage))
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
        assertThatThrownBy(() -> load(storage))
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
        assertThatThrownBy(() -> load(storage))
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

        LoadedWorkspace workspace = load(new CsvStorageManager(tempDir));

        assertThat(workspace.projectRepository().findAll()).hasSize(1);
        assertThat(workspace.taskRepository().findAll()).hasSize(1);
        assertThat(workspace.taskRepository().findById(taskId).orElseThrow().getWorklogs()).hasSize(1);
    }

    @Test
    void ignoreTrailingEmptyLinesInCrlfCsvFile() throws IOException {
        UUID projectId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\r\n" + projectId + ",Project,Desc\r\n\r\n\r\n");

        LoadedWorkspace workspace = load(new CsvStorageManager(tempDir));

        assertThat(workspace.projectRepository().findAll())
                .extracting(Project::getId)
                .containsExactly(projectId);
    }

    @Test
    void rejectDelimiterOnlyRowInProjectsCsv() throws IOException {
        Files.writeString(tempDir.resolve("projects.csv"),
                "id,name,description\n" + UUID.randomUUID() + ",Project,Desc\n,,\n");

        assertThatThrownBy(() -> load(new CsvStorageManager(tempDir)))
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

        assertThatThrownBy(() -> load(new CsvStorageManager(tempDir)))
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

        assertThatThrownBy(() -> load(new CsvStorageManager(tempDir)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Empty record in worklogs.csv at line 3")
                .hasMessageContaining("only delimiters");
    }
    private static void save(CsvStorageManager storage, ProjectRepository projectRepository, TaskRepository taskRepository) {
        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            projectRepository.findAll().forEach(unitOfWork.projects()::save);
            taskRepository.findAll().forEach(unitOfWork.tasks()::save);
            unitOfWork.commit();
        }
    }

    private static LoadedWorkspace load(CsvStorageManager storage) {
        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            InMemoryProjectRepository projects = new InMemoryProjectRepository();
            InMemoryTaskRepository tasks = new InMemoryTaskRepository();
            unitOfWork.projects().findAll().forEach(projects::save);
            unitOfWork.tasks().findAll().forEach(tasks::save);
            return new LoadedWorkspace(projects, tasks);
        }
    }

    private record LoadedWorkspace(ProjectRepository projectRepository, TaskRepository taskRepository) {
    }

}
