package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
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
import org.junit.jupiter.params.provider.ValueSource;

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

        cli = new TaskPulseCli(projectService, taskService, out, err);
    }

    @Test
    void helpCommandDisplaysAvailableCommands() {
        boolean continueRunning = cli.executeLine("help");
        assertThat(continueRunning).isTrue();
        assertThat(outContent.toString()).contains("Available commands:", "project add", "task add", "task update", "worklog log",
                "--exceeded [<minutes>]");
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
    void taskUpdateChangesOnlyProvidedFields() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Keep description", Priority.HIGH, 30,
                LocalDate.of(2026, 12, 1));

        cli.executeLine("task update " + task.getId() + " --title \"Changed title\" --estimate 45");

        Task updated = taskService.getTask(task.getId());
        assertThat(errContent.toString()).isEmpty();
        assertThat(outContent.toString()).contains("Task updated:");
        assertThat(updated.getTitle()).isEqualTo("Changed title");
        assertThat(updated.getDescription()).isEqualTo("Keep description");
        assertThat(updated.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(updated.getEstimatedMinutes()).isEqualTo(45);
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void taskUpdateRejectsInvalidValuesWithoutPartialChanges() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Description", Priority.HIGH, 30, null);

        cli.executeLine("task update " + task.getId() + " --title Changed --estimate -5");

        assertThat(errContent.toString()).contains("Estimated minutes cannot be negative");
        assertThat(taskService.getTask(task.getId()).getTitle()).isEqualTo("Original");
        assertThat(taskService.getTask(task.getId()).getEstimatedMinutes()).isEqualTo(30);
    }

    @Test
    void taskUpdateRejectsBlankTitleWithoutModifyingTask() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Description", Priority.HIGH, 30, null);

        cli.executeLine("task update " + task.getId() + " --title \"   \" --priority low");

        assertThat(errContent.toString()).contains("Task title must not be blank");
        assertThat(taskService.getTask(task.getId()).getTitle()).isEqualTo("Original");
        assertThat(taskService.getTask(task.getId()).getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void taskUpdateCanClearOptionalEstimateAndDueDate() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Description", Priority.HIGH, 30,
                LocalDate.of(2026, 12, 1));

        cli.executeLine("task update " + task.getId() + " --estimate none --due none");

        assertThat(errContent.toString()).isEmpty();
        assertThat(taskService.getTask(task.getId()).getEstimatedMinutes()).isNull();
        assertThat(taskService.getTask(task.getId()).getDueDate()).isNull();
    }

    @Test
    void taskUpdateRejectsUnknownOrIncompleteOptions() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "", Priority.MEDIUM, null, null);

        cli.executeLine("task update " + task.getId() + " --bogus value");
        assertThat(errContent.toString()).contains("Unknown task update option");
        errContent.reset();

        cli.executeLine("task update " + task.getId() + " --priority --title Changed");
        assertThat(errContent.toString()).contains("Missing value for --priority");
        assertThat(taskService.getTask(task.getId()).getTitle()).isEqualTo("Original");
    }


    @Test
    void taskWithoutSubcommandDisplaysUsage() {
        cli.executeLine("task");
        assertThat(errContent.toString()).contains("Usage: task <add|update|list|show|transition|label|delete>");
    }

    @Test
    void taskUpdateWithoutRequiredArgumentsDisplaysUsage() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Description", Priority.HIGH, 30, null);

        cli.executeLine("task update");
        assertThat(errContent.toString()).contains("Usage: task update");
        errContent.reset();
        cli.executeLine("task update " + task.getId());
        assertThat(errContent.toString()).contains("Usage: task update");
        assertThat(taskService.getTask(task.getId()).getTitle()).isEqualTo("Original");
    }

    @Test
    void taskUpdateChangesDescriptionAndSetsDueDate() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Old description", Priority.HIGH, 30, null);

        cli.executeLine("task update " + task.getId()
                + " --description \"New description\" --due 2026-12-15");

        assertThat(errContent.toString()).isEmpty();
        Task updated = taskService.getTask(task.getId());
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(updated.getTitle()).isEqualTo("Original");
        assertThat(updated.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(updated.getEstimatedMinutes()).isEqualTo(30);
    }

    @Test
    void taskUpdateRejectsNonNumericEstimateWithoutChangingTask() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Description", Priority.HIGH, 30, null);

        cli.executeLine("task update " + task.getId() + " --title Changed --estimate abc");

        assertThat(errContent.toString()).contains("Invalid estimate");
        assertThat(taskService.getTask(task.getId()).getTitle()).isEqualTo("Original");
        assertThat(taskService.getTask(task.getId()).getEstimatedMinutes()).isEqualTo(30);
    }

    @Test
    void taskUpdateRejectsMalformedDateAndInvalidPriority() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Description", Priority.HIGH, 30, null);

        cli.executeLine("task update " + task.getId() + " --due invalid-date");
        assertThat(errContent.toString()).contains("Invalid date");
        errContent.reset();

        cli.executeLine("task update " + task.getId() + " --priority INVALID");
        assertThat(errContent.toString()).contains("Invalid priority");
        assertThat(taskService.getTask(task.getId()).getDueDate()).isNull();
        assertThat(taskService.getTask(task.getId()).getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void taskUpdateRejectsMissingOptionValueAtEnd() {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Description", Priority.HIGH, 30, null);

        cli.executeLine("task update " + task.getId() + " --due");
        assertThat(errContent.toString()).contains("Usage: task update");
        errContent.reset();

        cli.executeLine("task update " + task.getId() + " --title Changed --due");
        assertThat(errContent.toString()).contains("Missing value for --due");
        assertThat(taskService.getTask(task.getId()).getTitle()).isEqualTo("Original");
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

    @ParameterizedTest
    @ValueSource(strings = {"\"Course", "'Course"})
    void unclosedQuoteRejectsProjectCreation(String argument) {
        boolean keepRunning = cli.executeLine("project add " + argument);

        assertThat(keepRunning).isTrue();
        assertThat(projectService.listProjects()).isEmpty();
        assertThat(outContent.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(errContent.toString(StandardCharsets.UTF_8))
                .contains("Invalid argument: Unclosed", "quote")
                .doesNotContain("Exception in thread", "at cz.upce");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"Changed", "'Changed"})
    void unclosedQuoteRejectsTaskUpdate(String argument) {
        UUID projectId = projectService.createProject("Course", "").getId();
        Task task = taskService.createTask(projectId, "Original", "Description", Priority.HIGH, 30, null);

        cli.executeLine("task update " + task.getId() + " --estimate 60 --title " + argument);

        assertThat(task.getTitle()).isEqualTo("Original");
        assertThat(task.getEstimatedMinutes()).isEqualTo(30);
        assertThat(outContent.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(errContent.toString(StandardCharsets.UTF_8))
                .contains("Invalid argument: Unclosed", "quote");
    }

    @Test
    void interactiveSessionContinuesAfterUnclosedQuote() {
        cli.runInteractive(new Scanner("project add \"Broken\nproject add \"Valid project\"\nexit\n"));

        assertThat(projectService.listProjects())
                .extracting(Project::getName)
                .containsExactly("Valid project");
        assertThat(outContent.toString(StandardCharsets.UTF_8)).contains("Project created:", "Valid project")
                .doesNotContain("Broken");
        assertThat(errContent.toString(StandardCharsets.UTF_8))
                .contains("Invalid argument: Unclosed", "quote");
    }

    @Test
    void projectEditWithoutDescriptionKeepsExistingDescription() {
        UUID projectId = projectService.createProject("Course", "Semester project").getId();

        cli.executeLine("project edit " + projectId + " \"Renamed course\"");

        var project = projectService.getProject(projectId);
        assertThat(project.getName()).isEqualTo("Renamed course");
        assertThat(project.getDescription()).isEqualTo("Semester project");
        assertThat(outContent.toString()).contains("Project updated:", "Renamed course");
    }

    @Test
    void projectEditWithDescriptionReplacesIt() {
        UUID projectId = projectService.createProject("Course", "Semester project").getId();

        cli.executeLine("project edit " + projectId + " Course \"Final exam\"");

        assertThat(projectService.getProject(projectId).getDescription()).isEqualTo("Final exam");
    }

    @Test
    void projectEditWithEmptyDescriptionClearsIt() {
        UUID projectId = projectService.createProject("Course", "Semester project").getId();

        cli.executeLine("project edit " + projectId + " Course \"\"");

        assertThat(projectService.getProject(projectId).getDescription()).isEmpty();
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
    void taskAddSetsAttributesFromQuotedCommandLine() {
        UUID projectId = projectService.createProject("Course", "").getId();

        cli.executeLine("task add " + projectId + " \"Implement parser\" \"RFC 4180, quoted fields\""
                + " --priority CRITICAL --estimate 120 --due 2026-12-01");

        Task task = taskService.listTasks().get(0);
        assertThat(task.getTitle()).isEqualTo("Implement parser");
        assertThat(task.getDescription()).isEqualTo("RFC 4180, quoted fields");
        assertThat(task.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(task.getEstimatedMinutes()).isEqualTo(120);
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void taskAddWithInvalidOptionReportsErrorAndCreatesNothing() {
        UUID projectId = projectService.createProject("Course", "").getId();

        cli.executeLine("task add " + projectId + " Parser --due tomorrow");

        assertThat(errContent.toString(StandardCharsets.UTF_8))
                .contains("Invalid argument: Invalid date: 'tomorrow'")
                .doesNotContain("Exception");
        assertThat(taskService.listTasks()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "--author --desc oprava, --author",
            "--desc --author testautor, --desc",
            "--author testautor --desc --author jinyautor, --desc"
    })
    void worklogRejectsOptionAsValueWithoutLoggingWork(String options, String missingValueOption) {
        var project = projectService.createProject("Course", "");
        Task task = taskService.createTask(project.getId(), "Review", "", Priority.MEDIUM, null, null);
        taskService.transitionTask(task.getId(), TaskState.IN_PROGRESS);

        cli.executeLine("worklog log " + task.getId() + " 30 " + options);

        assertThat(errContent.toString()).contains("Invalid argument: Missing value for option " + missingValueOption);
        assertThat(outContent.toString()).isEmpty();
        assertThat(taskService.getTask(task.getId()).getWorklogs()).isEmpty();
        assertThat(taskService.getTask(task.getId()).getTotalRecordedMinutes()).isZero();
    }

    @Test
    void equalsSyntaxAcceptsQuotedSwitchLikeValues() {
        var project = projectService.createProject("Course", "");
        Task task = taskService.createTask(project.getId(), "Fix --overdue flag", "", Priority.MEDIUM, null, null);
        taskService.transitionTask(task.getId(), TaskState.IN_PROGRESS);

        cli.executeLine("worklog log " + task.getId() + " 30 --author Dev --desc=\"--verbose details\"");
        cli.executeLine("task list --search=\"--overdue flag\"");

        assertThat(taskService.getTask(task.getId()).getWorklogs().get(0).description())
                .isEqualTo("--verbose details");
        assertThat(outContent.toString()).contains("Fix --overdue flag");
        assertThat(errContent.toString()).isEmpty();
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
    @CsvSource({
            "--priority, Missing value for option --priority",
            "--from, Missing value for option --from",
            "--to, Missing value for option --to",
            "--prioirty HIGH, Unknown option: --prioirty",
            "--prioirty=HIGH, Unknown option: --prioirty"
    })
    void taskListRejectsInvalidOptions(String options, String expectedError) {
        taskWith45RecordedMinutes(30);

        cli.executeLine("task list " + options);

        assertThat(errContent.toString()).contains(expectedError);
        assertThat(outContent.toString()).isEmpty();
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
            "--from 2999-01-10 --to 2999-01-10, Soon"
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
}
