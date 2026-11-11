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
import org.junit.jupiter.params.provider.ValueSource;
import cz.upce.fei.nnptp.taskpulse.storage.csv.CsvStorageManager;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskListOverdueTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private ProjectService projects;
    private TaskService tasks;
    private TaskPulseCli cli;
    private UUID projectId;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        var projectRepository = new InMemoryProjectRepository();
        var taskRepository = new InMemoryTaskRepository();
        projects = new ProjectService(projectRepository, taskRepository);
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T23:30:00Z"), ZoneId.of("Europe/Prague"));
        tasks = new TaskService(taskRepository, projectRepository, clock);
        projectId = projects.createProject("Course", "").getId();
        CsvStorageManager storageManager = new CsvStorageManager(tempDir);
        cli = new TaskPulseCli(projectRepository, taskRepository, projects, tasks, storageManager,
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--overdue", "--OVERDUE"})
    void listsOnlyUnfinishedTasksBeforeToday(String flag) {
        Task todo = createTask("Late todo", TODAY.minusDays(1));
        Task active = createTask("Late active", TODAY.minusDays(2));
        tasks.transitionTask(active.getId(), TaskState.IN_PROGRESS);
        Task blocked = createTask("Late blocked", TODAY.minusDays(3));
        tasks.transitionTask(blocked.getId(), TaskState.IN_PROGRESS);
        tasks.transitionTask(blocked.getId(), TaskState.BLOCKED);
        Task done = createTask("Completed", TODAY.minusDays(1));
        tasks.transitionTask(done.getId(), TaskState.IN_PROGRESS);
        tasks.logWork(done.getId(), 10, "Developer", "");
        tasks.transitionTask(done.getId(), TaskState.DONE);
        Task today = createTask("Due today", TODAY);
        Task future = createTask("Future", TODAY.plusDays(1));
        Task undated = createTask("Undated", null);

        assertThat(cli.executeLine("task list " + flag)).isTrue();

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains(todo.getId().toString(), active.getId().toString(), blocked.getId().toString())
                .doesNotContain(done.getId().toString(), today.getId().toString(),
                        future.getId().toString(), undated.getId().toString());
        assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(tasks.listTasks()).hasSize(7);
        assertThat(todo.getDueDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(todo.getState()).isEqualTo(TaskState.TODO);
        assertThat(done.getTotalRecordedMinutes()).isEqualTo(10);
    }

    @Test
    void combinesWithProjectPriorityAndSort() {
        Task later = createTask("Alpha", TODAY.minusDays(1));
        Task earlier = createTask("Zulu", TODAY.minusDays(2));
        Task low = tasks.createTask(projectId, "Low", "", Priority.LOW, null, TODAY.minusDays(1));
        UUID otherProject = projects.createProject("Other", "").getId();
        Task other = tasks.createTask(otherProject, "Other", "", Priority.HIGH, null, TODAY.minusDays(1));

        cli.executeLine("task list --project " + projectId + " --overdue --priority HIGH --sort duedate");

        assertThat(output.toString(StandardCharsets.UTF_8))
                .containsSubsequence(earlier.getId().toString(), later.getId().toString())
                .doesNotContain(low.getId().toString(), other.getId().toString());
        assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void reportsNoMatches() {
        createTask("Due today", TODAY);
        cli.executeLine("task list --overdue");
        assertThat(output.toString(StandardCharsets.UTF_8).strip()).isEqualTo("No tasks found.");
        assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void emptyWorkspaceReportsNoMatches() {
        cli.executeLine("task list --overdue");
        assertThat(output.toString(StandardCharsets.UTF_8).strip()).isEqualTo("No tasks found.");
        assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void listingWithoutFlagIncludesTasksThatAreNotOverdue() {
        Task late = createTask("Late", TODAY.minusDays(1));
        Task today = createTask("Today", TODAY);
        Task undated = createTask("Undated", null);
        cli.executeLine("task list");
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains(late.getId().toString(), today.getId().toString(), undated.getId().toString());
    }

    @Test
    void helpDocumentsFlag() {
        cli.executeLine("help");
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("[--overdue]");
    }

    private Task createTask(String title, LocalDate dueDate) {
        return tasks.createTask(projectId, title, "", Priority.HIGH, null, dueDate);
    }
}
