package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.csv.WorkspaceBackup;

import java.io.PrintStream;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;

public class TaskPulseCli {
    private final Map<String, CliCommandHandler> handlers = new LinkedHashMap<>();
    private final PrintStream out;
    private final PrintStream err;
    private final CliErrorHandler errorHandler;

    public TaskPulseCli(ProjectService projectService, TaskService taskService, PrintStream out, PrintStream err) {
        this(projectService, taskService, out, err, Clock.systemDefaultZone());
    }

    public TaskPulseCli(ProjectService projectService, TaskService taskService, PrintStream out, PrintStream err,
                        Clock clock) {
        Objects.requireNonNull(projectService, "ProjectService must not be null");
        Objects.requireNonNull(taskService, "TaskService must not be null");
        Objects.requireNonNull(clock, "Clock must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.err = Objects.requireNonNull(err, "err must not be null");
        this.errorHandler = new CliErrorHandler(this.err);
        handlers.put("project", new ProjectCliHandler(projectService, out, err));
        handlers.put("task", new TaskCliHandler(taskService, out, err));
        handlers.put("worklog", new WorklogCliHandler(taskService, out, err));
        handlers.put("report", new ReportCliHandler(projectService, taskService, out, err, clock));
    }

    public TaskPulseCli(ProjectService projectService, TaskService taskService) {
        this(projectService, taskService, System.out, System.err);
    }

    public TaskPulseCli(ProjectService projectService, TaskService taskService,
                        WorkspaceBackup backup, PrintStream out, PrintStream err) {
        this(projectService, taskService, out, err);
        handlers.put("workspace", new WorkspaceCliHandler(backup, out));
    }

    public void runInteractive(Scanner scanner) {
        out.println("TaskPulse CLI — type 'help' for commands, 'exit' to quit.");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            boolean keepRunning = executeLine(line);
            if (!keepRunning) {
                break;
            }
        }
    }

    public boolean executeLine(String line) {
        if (line == null) {
            return false;
        }
        line = line.strip();
        if (line.isEmpty()) {
            return true;
        }

        List<String> tokens;
        try {
            tokens = CommandTokenizer.tokenize(line);
        } catch (IllegalArgumentException e) {
            errorHandler.handle(e);
            return true;
        }
        return executeTokens(tokens);
    }

    public boolean executeTokens(List<String> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }

        String command = tokens.get(0).toLowerCase(Locale.ROOT);
        if (command.equals("exit") || command.equals("quit")) {
            return false;
        }

        try {
            switch (command) {
                case "help" -> printHelp();
                default -> {
                    CliCommandHandler handler = handlers.get(command);
                    if (handler == null) {
                        err.println("Unknown command: '" + command + "'. Type 'help' for available commands.");
                    } else {
                        handler.handle(tokens.subList(1, tokens.size()));
                    }
                }
            }
        } catch (Exception e) {
            errorHandler.handle(e);
        }
        return true;
    }

    private void printHelp() {
        out.println("Available commands:");
        out.println("  project add <name> [description]");
        out.println("  project list");
        out.println("  project show <project-id>");
        out.println("  project edit <project-id> <name> [description]");
        out.println("  project delete <project-id>");
        out.println("  task add <project-id> <title> [description] [--priority <priority>] [--estimate <minutes>] [--due <YYYY-MM-DD>]");
        out.println("  task list [--project <id>] [--state <state>] [--priority <priority>] [--label <label>]"
                + " [--search <text>] [--overdue] [--exceeded [<minutes>]] [--sort <title|priority|duedate|priority-duedate>]"
                + " [--from <YYYY-MM-DD>] [--to <YYYY-MM-DD>]");
        out.println("  task update <task-id> [--title <title>] [--description <text>] [--priority <priority>] [--estimate <minutes|none>] [--due <YYYY-MM-DD|none>]");
        out.println("  task show <task-id>");
        out.println("  task transition <task-id> <state>");
        out.println("  task label add <task-id> <label>");
        out.println("  task label remove <task-id> <label>");
        out.println("  task delete <task-id>");
        out.println("  worklog log <task-id> <durationMinutes> --author <author> [--desc <description>]");
        out.println("  worklog list <task-id>");
        out.println("  " + ReportCliHandler.USAGE);
        out.println("    Without --output, prints to the console. Existing files are never overwritten.");
        out.println("  workspace export <archive.zip>");
        out.println("  workspace import <archive.zip|csv-directory> (replaces current workspace)");
        out.println("  exit / quit");
    }

    static UUID parseUuid(String val) {
        try {
            return UUID.fromString(val.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: '" + val + "'");
        }
    }
}
