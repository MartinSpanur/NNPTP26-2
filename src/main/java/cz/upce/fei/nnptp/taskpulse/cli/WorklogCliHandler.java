package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class WorklogCliHandler implements CliCommandHandler {
    private final TaskService taskService;
    private final PrintStream out;
    private final PrintStream err;

    WorklogCliHandler(TaskService taskService, PrintStream out, PrintStream err) {
        this.taskService = taskService;
        this.out = out;
        this.err = err;
    }

    @Override
    public void handle(List<String> args) {
        if (args.isEmpty()) {
            err.println("Usage: worklog <log|list> [arguments]");
            return;
        }

        String action = args.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "log" -> {
                if (args.size() < 3) {
                    err.println("Usage: worklog log <task-id> <durationMinutes> --author <author> [--desc <description>]");
                    return;
                }
                UUID taskId = TaskPulseCli.parseUuid(args.get(1));
                long duration;
                try {
                    duration = Long.parseLong(args.get(2));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Duration must be a valid integer number of minutes");
                }
                CliOptions options = CliOptions.parse(args, 3);
                String author = options.get("--author");
                String desc = options.getOrDefault("--desc", "");
                if (author == null) {
                    err.println("Missing required --author argument");
                    return;
                }
                Worklog worklog = taskService.logWork(taskId, duration, author, desc);
                out.println("Work logged: " + worklog.durationMinutes() + " min by " + worklog.author() + " on task " + taskId);
            }
            case "list" -> {
                if (args.size() < 2) {
                    err.println("Usage: worklog list <task-id>");
                    return;
                }
                UUID taskId = TaskPulseCli.parseUuid(args.get(1));
                Task task = taskService.getTask(taskId);
                List<Worklog> worklogs = task.getWorklogs();
                if (worklogs.isEmpty()) {
                    out.println("No worklogs for task " + taskId);
                } else {
                    for (Worklog w : worklogs) {
                        out.println(w.timestamp() + " | " + w.durationMinutes() + " min | " + w.author() + (w.description().isEmpty() ? "" : " | " + w.description()));
                    }
                }
            }
            
            default -> err.println("Unknown worklog action: '" + action + "'");
        }
    }
}
