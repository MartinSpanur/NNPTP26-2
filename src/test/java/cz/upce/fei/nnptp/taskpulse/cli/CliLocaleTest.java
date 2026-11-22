package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskQuery;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CliLocaleTest {

    private Locale originalLocale;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private ProjectService projectService;
    private TaskService taskService;
    private TaskPulseCli cli;

    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        InMemoryProjectRepository projectRepo = new InMemoryProjectRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();
        projectService = new ProjectService(projectRepo, taskRepo);
        taskService = new TaskService(taskRepo, projectRepo);
        cli = new TaskPulseCli(projectService, taskService,
                new PrintStream(outContent, true, StandardCharsets.UTF_8),
                new PrintStream(errContent, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void uppercaseCommandsWorkInTurkishLocale() {
        Project project = projectService.createProject("Demo", "");
        taskService.createTask(project.getId(), "Write tests", "", null, null, null);

        cli.executeLine("TASK LIST");
        cli.executeLine("PROJECT LIST");

        assertThat(errContent.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(outContent.toString(StandardCharsets.UTF_8)).contains("Write tests", "Demo");
    }

    @Test
    void exitCommandWorksInTurkishLocale() {
        assertThat(cli.executeLine("EXIT")).isFalse();
        assertThat(cli.executeLine("QUIT")).isFalse();
    }

    @Test
    void sortOrderIsParsedInTurkishLocale() {
        cli.executeLine("task list --sort PRIORITY");
        cli.executeLine("task list --sort TITLE");

        assertThat(errContent.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void textSearchMatchesUppercaseQueryInTurkishLocale() {
        Project project = projectService.createProject("Demo", "");
        Task task = taskService.createTask(project.getId(), "Fix issue", "", null, null, null);

        assertThat(taskService.findTasks(TaskQuery.builder().textSearch("ISSUE").build())).containsExactly(task);
    }
}
