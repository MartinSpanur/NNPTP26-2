package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import cz.upce.fei.nnptp.taskpulse.storage.csv.CsvStorageManager;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPulseCliTest {

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private ProjectService projectService;
    private TaskService taskService;
    private TaskPulseCli cli;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outContent, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errContent, true, StandardCharsets.UTF_8);

        InMemoryProjectRepository projectRepo = new InMemoryProjectRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();
        projectService = new ProjectService(projectRepo, taskRepo);
        taskService = new TaskService(taskRepo, projectRepo);
        CsvStorageManager storageManager = new CsvStorageManager(tempDir);

        cli = new TaskPulseCli(projectRepo, taskRepo, projectService, taskService, storageManager, out, err);
    }

    @Test
    void helpCommandDisplaysAvailableCommands() {
        boolean continueRunning = cli.executeLine("help");
        assertThat(continueRunning).isTrue();
        assertThat(outContent.toString()).contains(
            "Available commands:", 
                "project add", 
                "task add", 
                "worklog log",
                "--exceeded [<minutes>]",
                "workspace export <archive.zip>",
                "workspace import <archive.zip>");
    }

    @Test
    void enumArgumentsAreCaseInsensitiveAndLocaleIndependent() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var project = projectService.createProject("Course", "");
            Task task = taskService.createTask(project.getId(), "Review", "", Priority.HIGH, null, null);

            cli.executeLine("task transition " + task.getId() + " in_progress");
            assertThat(task.getState()).isEqualTo(TaskState.IN_PROGRESS);
            assertThat(errContent.toString()).isEmpty();

            outContent.reset();
            cli.executeLine("task list --priority critical");
            assertThat(outContent.toString()).contains("No tasks found.");
            assertThat(errContent.toString()).isEmpty();
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void exitCommandReturnsFalse() {
        boolean continueRunning = cli.executeLine("exit");
        assertThat(continueRunning).isFalse();

        continueRunning = cli.executeLine("quit");
        assertThat(continueRunning).isFalse();
    }

    @Test
    void projectLifecycleViaCli() {
        cli.executeLine("project add \"Programming Course\" \"Semester project\"");
        assertThat(outContent.toString()).contains("Project created:", "Programming Course");

        outContent.reset();
        cli.executeLine("project list");
        assertThat(outContent.toString()).contains("Programming Course", "Semester project");
    }

    @Test
    void taskAndWorklogLifecycleViaCli() {
        var project = projectService.createProject("Course", "Desc");
        String projectId = project.getId().toString();

        cli.executeLine("task add " + projectId + " \"Implement parser\" \"RFC 4180\"");
        assertThat(outContent.toString()).contains("Task created:", "Implement parser");

        var task = taskService.listTasks().get(0);
        String taskId = task.getId().toString();

        cli.executeLine("task transition " + taskId + " IN_PROGRESS");
        assertThat(outContent.toString()).contains("transitioned to IN_PROGRESS");

        cli.executeLine("worklog log " + taskId + " 45 --author \"Student\" --desc \"First pass\"");
        assertThat(outContent.toString()).contains("Work logged: 45 min by Student");

        cli.executeLine("task transition " + taskId + " DONE");
        assertThat(outContent.toString()).contains("transitioned to DONE");
    }

    @Test
    void taskListWithoutSortOrdersByTitle() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Beta", "", Priority.LOW, null, null);
        taskService.createTask(projectId, "Alpha", "", Priority.HIGH, null, null);

        cli.executeLine("task list");

        assertThat(outContent.toString()).containsSubsequence("Alpha", "Beta");
    }

    @Test
    void taskListSortsByPriority() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Alpha", "", Priority.LOW, null, null);
        taskService.createTask(projectId, "Beta", "", Priority.CRITICAL, null, null);
        taskService.createTask(projectId, "Gamma", "", Priority.MEDIUM, null, null);

        cli.executeLine("task list --sort priority");

        assertThat(outContent.toString()).containsSubsequence("Beta", "Gamma", "Alpha");
    }

    @Test
    void taskListSortsByDueDateWithMissingDatesLast() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Alpha", "", Priority.MEDIUM, null, null);
        taskService.createTask(projectId, "Beta", "", Priority.MEDIUM, null, LocalDate.of(2026, 12, 1));
        taskService.createTask(projectId, "Gamma", "", Priority.MEDIUM, null, LocalDate.of(2026, 11, 1));

        cli.executeLine("task list --sort duedate");

        assertThat(outContent.toString()).containsSubsequence("Gamma", "Beta", "Alpha");
    }

    @Test
    void taskListSortsByPriorityThenDueDate() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Alpha", "", Priority.HIGH, null, LocalDate.of(2026, 12, 1));
        taskService.createTask(projectId, "Beta", "", Priority.LOW, null, LocalDate.of(2026, 10, 1));
        taskService.createTask(projectId, "Gamma", "", Priority.HIGH, null, LocalDate.of(2026, 11, 1));

        cli.executeLine("task list --sort priority-duedate");

        assertThat(outContent.toString()).containsSubsequence("Gamma", "Alpha", "Beta");
    }

    @Test
    void taskListSortValueIsCaseInsensitive() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Alpha", "", Priority.LOW, null, null);
        taskService.createTask(projectId, "Beta", "", Priority.HIGH, null, null);

        cli.executeLine("task list --sort PRIORITY");

        assertThat(errContent.toString()).isEmpty();
        assertThat(outContent.toString()).containsSubsequence("Beta", "Alpha");
    }

    @Test
    void taskListWithInvalidSortDisplaysAllowedValues() {
        cli.executeLine("task list --sort size");

        assertThat(errContent.toString()).contains("Invalid sort order: 'size'", "title", "priority", "duedate", "priority-duedate");
    }

    @Test
    void unknownCommandDisplaysClearErrorMessage() {
        cli.executeLine("unknownCommand 123");
        assertThat(errContent.toString()).contains("Unknown command: 'unknowncommand'");
    }

    @Test
    void invalidUuidDisplaysClearErrorMessageWithoutStackTrace() {
        cli.executeLine("project show not-a-uuid");
        assertThat(errContent.toString()).contains("Invalid UUID format: 'not-a-uuid'");
        assertThat(errContent.toString()).doesNotContain("Exception in thread", "at cz.upce");
    }

    @Test
    void interactiveSessionCleanExitOnEof() {
        Scanner scanner = new Scanner("help\nexit\n");
        cli.runInteractive(scanner);
        assertThat(outContent.toString()).contains("TaskPulse CLI", "Available commands:");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "60, 60 min, -15 min",
            "0, 0 min, 45 min",
            "none, not estimated, unavailable"
    }, nullValues = "none")
    void taskShowReportsEstimateActualAndVariance(Integer estimate, String expectedEstimate, String expectedVariance) {
        Task task = taskWith45RecordedMinutes(estimate);
        cli.executeLine("task show " + task.getId());
        assertThat(outContent.toString()).contains("Estimate:    " + expectedEstimate, "2 (45 min recorded)",
                "Variance:    " + expectedVariance);
    }

    @ParameterizedTest
    @CsvSource({
            "'', Review",
            "--exceeded --priority HIGH, Review | 15 min over estimate",
            "--exceeded 14, Review | 15 min over estimate",
            "--exceeded 15, No tasks found.",
            "--priority LOW --exceeded, No tasks found."
    })
    void taskListShowsOnlyMatchingVarianceAlerts(String options, String expectedOutput) {
        taskWith45RecordedMinutes(30);
        cli.executeLine("task list " + options);
        assertThat(outContent.toString().stripTrailing()).endsWith(expectedOutput);
        assertThat(errContent.toString()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"-1, non-negative", "later, whole number", "9223372036854775808, whole number"})
    void taskListRejectsInvalidVarianceThreshold(String input, String expectedError) {
        cli.executeLine("task list --exceeded " + input);
        assertThat(errContent.toString()).contains(expectedError);
        assertThat(outContent.toString()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "'', None;Past;Soon;Later",
            "--from 2999-01-01, Later;Soon",
            "--to 2999-01-31, Past;Soon",
            "--from 2999-01-01 --to 2999-01-31, Soon",
            "--from 2999-01-10 --to 2999-01-10, Soon",
            "--from, None;Past;Soon;Later",
            "--to, None;Past;Soon;Later"
    })
    void taskListFiltersByDueDateRange(String options, String expectedTitles) {
        tasksWithDueDates();
        cli.executeLine("task list " + options);
        assertThat(errContent.toString()).isEmpty();
        assertThat(listedTitles()).containsExactlyInAnyOrder(
                expectedTitles.isEmpty() ? new String[0] : expectedTitles.split(";"));
    }

    @ParameterizedTest
    @CsvSource({
            "--from 2999-13-01, Invalid date: '2999-13-01'",
            "--to 10.1.2999, Invalid date: '10.1.2999'",
            "--from 2999-02-01 --to 2999-01-01, Invalid date range"
    })
    void taskListRejectsInvalidDueDateFilters(String options, String expectedError) {
        cli.executeLine("task list " + options);
        assertThat(errContent.toString()).contains(expectedError);
        assertThat(outContent.toString()).isEmpty();
    }

    private void tasksWithDueDates() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "None", "", Priority.MEDIUM, null, null);
        taskService.createTask(projectId, "Past", "", Priority.MEDIUM, null, LocalDate.of(2000, 1, 1));
        taskService.createTask(projectId, "Soon", "", Priority.MEDIUM, null, LocalDate.of(2999, 1, 10));
        taskService.createTask(projectId, "Later", "", Priority.MEDIUM, null, LocalDate.of(2999, 3, 1));
    }

    private List<String> listedTitles() {
        return outContent.toString().lines()
                .filter(line -> line.contains(" | "))
                .map(line -> line.substring(line.lastIndexOf(" | ") + 3))
                .toList();
    }

    private Task taskWith45RecordedMinutes(Integer estimate) {
        var project = projectService.createProject("Planning", "");
        Task task = taskService.createTask(project.getId(), "Review", "", Priority.HIGH, estimate, null);
        taskService.transitionTask(task.getId(), TaskState.IN_PROGRESS);
        taskService.logWork(task.getId(), 20, "Dev", "");
        taskService.logWork(task.getId(), 25, "Dev", "");
        return task;
    }
    
    @Test
    void workspaceExportCommandCreatesArchiveFromCurrentWorkspace() {
        createCurrentWorkspaceForExport();

        Path archive = tempDir.resolve("cli-backup.zip");

        boolean continueRunning = cli.executeLine(
                "workspace export " + quotedCliPath(archive)
        );

        assertThat(continueRunning).isTrue();
        assertThat(errors()).isEmpty();
        assertThat(archive).isRegularFile();
        assertThat(output())
                .contains("Workspace exported to:")
                .contains(archive.toAbsolutePath().normalize().toString());

        assertExportedWorkspace(importInto(archive, "export-check"));
    }
    
    @Test
    void workspaceImportCommandReplacesWorkspaceImmediately() {
        ImportFixture imported = createArchiveWithProjectAndTask(
                "import.zip",
                "Restored project",
                "Restored task"
        );

        projectService.createProject("Old project", "");

        cli.executeLine("workspace import " + quotedCliPath(imported.archive()));

        assertThat(errors()).isEmpty();
        assertThat(output())
                .contains("Workspace imported from:")
                .contains(
                        imported.archive()
                                .toAbsolutePath()
                                .normalize()
                                .toString()
                );

        outContent.reset();

        cli.executeLine("project list");
        cli.executeLine("task list");

        assertThat(output())
                .contains("Restored project", "Restored task", imported.projectId().toString(), imported.taskId().toString())
                .doesNotContain("Old project");

        assertThat(errors()).isEmpty();
    }
    
    @Test
    void importedWorkspaceRemainsActiveWhenSavedAgain() {
        Path archive = createArchiveWithProject(
                "save-import.zip",
                "Imported project"
        );

        cli.executeLine("workspace import " + quotedCliPath(archive));
        cli.executeLine("project add \"Created after import\" \"New data\"");
        cli.saveWorkspace();

        CsvStorageManager.LoadedWorkspace persisted =
                new CsvStorageManager(tempDir).load();

        assertThat(persisted.projectRepository().findAll())
                .extracting(project -> project.getName())
                .containsExactlyInAnyOrder("Imported project", "Created after import");
    }
    
    @Test
    void workspaceImportCommandReportsMissingArchive() {
        Path missingArchive = tempDir.resolve("missing.zip");

        cli.executeLine("workspace import \"" + missingArchive + "\"");

        assertThat(errors()).contains("Workspace archive does not exist");
        assertThat(output()).doesNotContain("Workspace imported from:");
    }
    
    
    
    // HELPERS
    
    
    private Path createArchiveWithProject(
        String archiveFileName,
        String projectName
    ) {
        Path sourceDirectory =
                tempDir.resolve(archiveFileName + "-source");
        Path archive = tempDir.resolve(archiveFileName);

        CsvStorageManager sourceStorage =
                new CsvStorageManager(sourceDirectory);

        var projects = new InMemoryProjectRepository();
        var tasks = new InMemoryTaskRepository();

        ProjectService projectsService =
                new ProjectService(projects, tasks);

        projectsService.createProject(projectName, "");

        sourceStorage.save(projects, tasks);
        sourceStorage.exportArchive(archive);

        return archive;
    }

    private ImportFixture createArchiveWithProjectAndTask(
            String archiveFileName,
            String projectName,
            String taskName
    ) {
        Path sourceDirectory =
                tempDir.resolve(archiveFileName + "-source");
        Path archive = tempDir.resolve(archiveFileName);

        CsvStorageManager sourceStorage =
                new CsvStorageManager(sourceDirectory);

        var projects = new InMemoryProjectRepository();
        var tasks = new InMemoryTaskRepository();

        ProjectService projectsService =
                new ProjectService(projects, tasks);

        TaskService tasksService =
                new TaskService(tasks, projects);

        var project = projectsService.createProject(
                projectName,
                "Imported through CLI"
        );

        Task task = tasksService.createTask(
                project.getId(),
                taskName,
                "",
                Priority.CRITICAL,
                60,
                null
        );

        sourceStorage.save(projects, tasks);
        sourceStorage.exportArchive(archive);

        return new ImportFixture(
                archive,
                project.getId(),
                task.getId()
        );
    }

    private String quotedCliPath(Path path) {
        String normalizedPath = path.toAbsolutePath()
                .normalize()
                .toString()
                .replace('\\', '/');

        return "\"" + normalizedPath + "\"";
    }

    private CsvStorageManager.LoadedWorkspace importInto(
            Path archive,
            String directoryName
    ) {
        CsvStorageManager storage =
                new CsvStorageManager(tempDir.resolve(directoryName));

        return storage.importArchive(archive);
    }

    private String output() {
        return outContent.toString(StandardCharsets.UTF_8);
    }

    private String errors() {
        return errContent.toString(StandardCharsets.UTF_8);
    }

    private record ImportFixture(
            Path archive,
            UUID projectId,
            UUID taskId
    ) {
    }
    
    private void createCurrentWorkspaceForExport() {
        var project = projectService.createProject(
                "Exported project",
                "CLI export test"
        );

        Task task = taskService.createTask(
                project.getId(),
                "Exported task",
                "",
                Priority.HIGH,
                30,
                null
        );

        taskService.transitionTask(
                task.getId(),
                TaskState.IN_PROGRESS
        );

        taskService.logWork(
                task.getId(),
                15,
                "Tester",
                "Export through CLI"
        );
    }

    private void assertExportedWorkspace(
            CsvStorageManager.LoadedWorkspace workspace
    ) {
        assertThat(workspace.projectRepository().findAll())
                .extracting(project -> project.getName())
                .containsExactly("Exported project");

        assertThat(workspace.taskRepository().findAll())
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getTitle())
                            .isEqualTo("Exported task");
                    assertThat(task.getWorklogs())
                            .hasSize(1);
                    assertThat(task.getTotalRecordedMinutes())
                            .isEqualTo(15);
                });
    }
}
