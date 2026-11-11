package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.csv.CsvStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CliEndToEndTest {

    private static final Pattern PROJECT_ID = Pattern.compile("Project created: (\\S+)");
    private static final Pattern TASK_ID = Pattern.compile("Task created: (\\S+)");

    @TempDir
    Path dataDir;

    private record Session(String out, String err) {
    }

    private Session runSession(String... lines) {
        CsvStorageManager storage = new CsvStorageManager(dataDir);
        CsvStorageManager.LoadedWorkspace workspace = storage.load();
        ProjectService projectService = new ProjectService(workspace.projectRepository(), workspace.taskRepository());
        TaskService taskService = new TaskService(workspace.taskRepository(), workspace.projectRepository());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        TaskPulseCli cli = new TaskPulseCli(projectService, taskService,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        String input = String.join("\n", lines) + "\n";
        cli.runInteractive(new Scanner(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        storage.save(workspace.projectRepository(), workspace.taskRepository());

        return new Session(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static String extract(Pattern pattern, String output) {
        Matcher matcher = pattern.matcher(output);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void completeTaskLifecycleAcrossSessions() {
        Session created = runSession("project add \"Semester work\" \"NNPTP project\"", "exit");
        assertThat(created.out()).startsWith("TaskPulse CLI");
        String projectId = extract(PROJECT_ID, created.out());

        Session taskSession = runSession("task add " + projectId + " \"Write tests\"", "exit");
        String taskId = extract(TASK_ID, taskSession.out());

        Session work = runSession(
                "task transition " + taskId + " in_progress",
                "task label add " + taskId + " testing",
                "worklog log " + taskId + " 45 --author \"Jan Novak\" --desc \"First pass\"",
                "task transition " + taskId + " DONE",
                "exit");

        assertThat(work.err()).isEmpty();
        assertThat(work.out()).contains(
                "Task " + taskId + " transitioned to IN_PROGRESS",
                "Label 'testing' added to task " + taskId,
                "Work logged: 45 min by Jan Novak on task " + taskId,
                "Task " + taskId + " transitioned to DONE");

        Session shown = runSession("task show " + taskId, "worklog list " + taskId, "exit");
        assertThat(shown.out()).contains(
                "Title:       Write tests",
                "State:       DONE",
                "Labels:      testing",
                "Worklogs:    1 (45 min recorded)",
                "45 min | Jan Novak | First pass");
    }

    @Test
    void listsAndFiltersTasksFromPipedInput() {
        String projectId = extract(PROJECT_ID, runSession("project add Backend", "exit").out());
        runSession("task add " + projectId + " \"Alpha task\"", "exit");
        String betaId = extract(TASK_ID, runSession("task add " + projectId + " \"Beta task\"", "exit").out());

        Session listed = runSession(
                "project list",
                "task transition " + betaId + " IN_PROGRESS",
                "task list --project " + projectId + " --state IN_PROGRESS",
                "task list --search alpha",
                "exit");

        assertThat(listed.out()).contains(projectId + " | Backend");
        assertThat(listed.out()).contains(betaId + " | IN_PROGRESS | MEDIUM | Beta task");
        assertThat(listed.out().lines().filter(l -> l.endsWith("| Alpha task"))).hasSize(1);
    }

    @Test
    void errorsGoToStderrAndSessionContinues() {
        Session session = runSession(
                "unknown",
                "task show not-a-uuid",
                "project delete 00000000-0000-0000-0000-000000000000",
                "worklog log 00000000-0000-0000-0000-000000000000 10",
                "project list",
                "exit");

        assertThat(session.err()).contains(
                "Unknown command: 'unknown'",
                "Invalid argument: Invalid UUID format: 'not-a-uuid'",
                "Error: Project not found with id: 00000000-0000-0000-0000-000000000000",
                "Missing required --author argument");
        assertThat(session.out()).contains("No projects found.");
    }

    @Test
    void invalidTransitionIsReportedAndStateIsKept() {
        String projectId = extract(PROJECT_ID, runSession("project add Demo", "exit").out());
        String taskId = extract(TASK_ID, runSession("task add " + projectId + " Task", "exit").out());

        Session session = runSession(
                "task transition " + taskId + " DONE",
                "task transition " + taskId + " IN_PROGRESS",
                "task transition " + taskId + " DONE",
                "task show " + taskId,
                "exit");

        assertThat(session.err()).contains(
                "Error: Invalid state transition from TODO to DONE",
                "Error: Cannot complete task in IN_PROGRESS state without any worklogs");
        assertThat(session.out()).contains("State:       IN_PROGRESS");
    }

    @Test
    void stopsReadingAfterExit() {
        Session session = runSession("help", "exit", "project add Ignored");

        assertThat(session.out()).contains("Available commands:");
        assertThat(runSession("project list", "exit").out()).contains("No projects found.");
    }

    @Test
    void endOfInputWithoutExitEndsSession() {
        Session session = runSession("project add Unterminated");

        assertThat(session.out()).contains("Project created:");
        assertThat(runSession("project list").out()).contains("Unterminated");
    }
}
