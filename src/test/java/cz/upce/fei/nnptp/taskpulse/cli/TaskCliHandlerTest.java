package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TaskCliHandlerTest {
    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private TaskService tasks;
    private TaskCliHandler handler;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        var projectRepo = new InMemoryProjectRepository();
        var taskRepo = new InMemoryTaskRepository();
        var projects = new ProjectService(projectRepo, taskRepo);
        tasks = new TaskService(taskRepo, projectRepo);
        projectId = projects.createProject("Course", "").getId();
        handler = new TaskCliHandler(tasks,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @Test
    void emptyAndIncompleteCommandsPrintUsage() {
        handler.handle(List.of());
        handler.handle(List.of("add", projectId.toString()));
        handler.handle(List.of("show"));
        handler.handle(List.of("transition", UUID.randomUUID().toString()));
        handler.handle(List.of("label", "add"));
        handler.handle(List.of("delete"));
        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("Usage: task <add|update|list|show|transition|label|delete>",
                        "Usage: task add", "Usage: task show", "Usage: task transition",
                        "Usage: task label", "Usage: task delete");
    }

    @Test
    void addDefaultsToMediumPriorityAndEmptyDescription() {
        handler.handle(List.of("ADD", projectId.toString(), "Implement"));
        var task = tasks.listTasks().get(0);
        assertThat(task.getTitle()).isEqualTo("Implement");
        assertThat(task.getDescription()).isEmpty();
        assertThat(task.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Task created:");
    }

    @Test
    void addAcceptsDescriptionPriorityEstimateAndDueDate() {
        handler.handle(List.of("add", projectId.toString(), "Implement", "CSV escaping",
                "--priority", "high", "--estimate", "90", "--due", "2026-12-01"));
        var task = tasks.listTasks().get(0);
        assertThat(task.getDescription()).isEqualTo("CSV escaping");
        assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(task.getEstimatedMinutes()).isEqualTo(90);
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void addAcceptsOptionsWithoutDescription() {
        handler.handle(List.of("add", projectId.toString(), "Implement", "--ESTIMATE", "0"));
        var task = tasks.listTasks().get(0);
        assertThat(task.getDescription()).isEmpty();
        assertThat(task.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(task.getEstimatedMinutes()).isZero();
        assertThat(task.getDueDate()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "--priority|URGENT|Invalid priority",
            "--estimate|soon|Invalid estimate",
            "--due|2026-02-30|Invalid date",
            "--deadline|2026-12-01|Unknown task add option: '--deadline'",
            "Description|extra|Unknown task add option: 'Description'"
    })
    void addRejectsInvalidOptionsWithoutCreatingTask(String option, String value, String message) {
        assertThatThrownBy(() -> handler.handle(List.of("add", projectId.toString(), "Implement", "", option, value)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
        assertThat(tasks.listTasks()).isEmpty();
    }

    @Test
    void addRejectsOptionWithoutValue() {
        assertThatThrownBy(() -> handler.handle(List.of("add", projectId.toString(), "Implement", "--priority")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing value for --priority");
        assertThatThrownBy(() -> handler.handle(List.of("add", projectId.toString(), "Implement", "--due", "--priority", "LOW")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing value for --due");
        assertThat(tasks.listTasks()).isEmpty();
    }

    @Test
    void addRejectsNegativeEstimateWithoutCreatingTask() {
        assertThatThrownBy(() -> handler.handle(List.of("add", projectId.toString(), "Implement", "--estimate", "-5")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("cannot be negative");
        assertThat(tasks.listTasks()).isEmpty();
    }


    @Test
    void searchAcceptsLiteralStartingWithDoubleDash() {
        tasks.createTask(projectId, "Fix --overdue flag", "", Priority.HIGH, null, null);
        tasks.createTask(projectId, "Unrelated", "", Priority.MEDIUM, null, null);
        handler.handle(List.of("list", "--search=--overdue"));
        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .contains("Fix --overdue flag")
                .doesNotContain("Unrelated");
    }

    @Test
    void invalidFilterValuesDescribeTheError() {
        assertThatThrownBy(() -> handler.handle(List.of("list", "--state", "INVALID")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid task state");
        assertThatThrownBy(() -> handler.handle(List.of("list", "--priority", "INVALID")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid priority");
        assertThatThrownBy(() -> handler.handle(List.of("list", "--sort", "INVALID")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid sort order");
        assertThatThrownBy(() -> handler.handle(List.of("list", "--from", "tomorrow")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid date");
        assertThatThrownBy(() -> handler.handle(List.of("list", "--exceeded", "several")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("--exceeded expects");
    }


    @Test
    void combinedProjectStateAndPriorityFiltersMatchOneTask() {
        var first = tasks.createTask(projectId, "Selected", "", Priority.HIGH, null, null);
        tasks.transitionTask(first.getId(), TaskState.IN_PROGRESS);
        tasks.createTask(projectId, "Other priority", "", Priority.LOW, null, null);
        handler.handle(List.of("list", "--project", projectId.toString(),
                "--state", "IN_PROGRESS", "--priority", "HIGH"));
        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .contains("Selected").doesNotContain("Other priority");
    }

    @Test
    void labelsCanBeAddedAndRemoved() {
        var task = tasks.createTask(projectId, "Review", "", Priority.MEDIUM, null, null);
        handler.handle(List.of("label", "ADD", task.getId().toString(), "backend"));
        assertThat(tasks.getTask(task.getId()).getLabels()).contains("backend");
        handler.handle(List.of("label", "remove", task.getId().toString(), "backend"));
        assertThat(tasks.getTask(task.getId()).getLabels()).doesNotContain("backend");
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Label 'backend' added", "Label 'backend' removed");
    }

    @Test
    void unknownLabelAndTaskActionsPrintErrors() {
        var task = tasks.createTask(projectId, "Review", "", Priority.MEDIUM, null, null);
        handler.handle(List.of("label", "rename", task.getId().toString(), "backend"));
        handler.handle(List.of("unknown"));
        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("Unknown label action: rename", "Unknown task action: 'unknown'");
        assertThat(tasks.getTask(task.getId()).getLabels()).isEmpty();
    }

    @Test
    void showWithoutEstimateAndLabelsDisplaysFallbacks() {
        var task = tasks.createTask(projectId, "Review", "", Priority.MEDIUM, null, null);
        handler.handle(List.of("show", task.getId().toString()));
        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .contains("Estimate:    not estimated", "Due date:    none",
                        "Labels:      none", "Variance:    unavailable");
    }

    @Test
    void deletionRemovesTask() {
        var task = tasks.createTask(projectId, "Temporary", "", Priority.MEDIUM, null, null);
        handler.handle(List.of("delete", task.getId().toString()));
        assertThat(tasks.listTasks()).isEmpty();
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Task deleted: " + task.getId());
    }
}
