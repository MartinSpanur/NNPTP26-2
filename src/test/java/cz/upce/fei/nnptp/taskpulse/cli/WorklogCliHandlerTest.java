package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class WorklogCliHandlerTest {
    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private TaskService tasks;
    private WorklogCliHandler handler;
    private UUID taskId;

    @BeforeEach
    void setUp() {
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        var projectRepo = new InMemoryProjectRepository();
        var taskRepo = new InMemoryTaskRepository();
        var projects = new ProjectService(projectRepo, taskRepo);
        tasks = new TaskService(taskRepo, projectRepo);
        taskId = tasks.createTask(projects.createProject("Course", "").getId(),
                "Implementation", "", Priority.MEDIUM, null, null).getId();
        tasks.transitionTask(taskId, TaskState.IN_PROGRESS);
        handler = new WorklogCliHandler(tasks,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @Test
    void missingArgumentsAndUnknownActionShowErrors() {
        handler.handle(List.of());
        handler.handle(List.of("log", taskId.toString()));
        handler.handle(List.of("list"));
        handler.handle(List.of("invalid"));
        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("Usage: worklog <log|list>", "Usage: worklog log",
                        "Usage: worklog list", "Unknown worklog action: 'invalid'");
    }

    @Test
    void listWithoutWorklogsPrintsMessage() {
        handler.handle(List.of("list", taskId.toString()));
        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .contains("No worklogs for task " + taskId);
    }

    @Test
    void missingAuthorDoesNotCreateWorklog() {
        handler.handle(List.of("log", taskId.toString(), "30"));
        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("Missing required --author argument");
        assertThat(tasks.getTask(taskId).getWorklogs()).isEmpty();
    }


    @Test
    void logWithoutDescriptionStoresEmptyDescription() {
        handler.handle(List.of("LOG", taskId.toString(), "25", "--author", "Dev"));
        var worklogs = tasks.getTask(taskId).getWorklogs();
        assertThat(worklogs).hasSize(1);
        assertThat(worklogs.get(0).durationMinutes()).isEqualTo(25);
        assertThat(worklogs.get(0).description()).isEmpty();
        stdout.reset();
        handler.handle(List.of("list", taskId.toString()));
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("25 min | Dev");
    }

    @Test
    void descriptionStartingWithDoubleDashIsStoredAndListed() {
        handler.handle(List.of("log", taskId.toString(), "40", "--author", "Dev",
                "--desc=--verbose"));
        assertThat(tasks.getTask(taskId).getWorklogs().get(0).description()).isEqualTo("--verbose");
        stdout.reset();
        handler.handle(List.of("list", taskId.toString()));
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("40 min | Dev | --verbose");
    }

    @Test
    void listDisplaysSeveralRecords() {
        tasks.logWork(taskId, 10, "Alice", "Review");
        tasks.logWork(taskId, 20, "Bob", "");
        handler.handle(List.of("list", taskId.toString()));
        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .contains("10 min | Alice | Review", "20 min | Bob")
                .doesNotContain("20 min | Bob | ");
    }
}
