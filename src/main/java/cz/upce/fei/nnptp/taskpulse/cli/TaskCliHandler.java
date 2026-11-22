package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.service.TaskQuery;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.service.TaskSortOrder;
import java.io.PrintStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class TaskCliHandler implements CliCommandHandler {
    private final TaskService taskService;
    private final PrintStream out;
    private final PrintStream err;

    TaskCliHandler(TaskService taskService, PrintStream out, PrintStream err) {
        this.taskService = taskService;
        this.out = out;
        this.err = err;
    }

    @Override
    public void handle(List<String> args) {
        if (args.isEmpty()) {
            err.println("Usage: task <add|update|list|show|transition|label|delete> [arguments]");
            return;
        }

        String action = args.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> {
                if (args.size() < 3) {
                    err.println("Usage: task add <project-id> <title> [description] [--priority <priority>] [--estimate <minutes>] [--due <YYYY-MM-DD>]");
                    return;
                }
                UUID projectId = TaskPulseCli.parseUuid(args.get(1));
                String title = args.get(2);
                int i = 3;
                String desc = "";
                if (i < args.size() && !args.get(i).startsWith("--")) {
                    desc = args.get(i++);
                }
                Priority priority = Priority.MEDIUM;
                Integer estimate = null;
                LocalDate dueDate = null;
                for (; i < args.size(); i += 2) {
                    String option = args.get(i).toLowerCase(Locale.ROOT);
                    switch (option) {
                        case "--priority" -> priority = parsePriority(optionValue(args, i, option));
                        case "--estimate" -> estimate = parseEstimate(optionValue(args, i, option));
                        case "--due" -> dueDate = parseDate(optionValue(args, i, option));
                        default -> throw new IllegalArgumentException("Unknown task add option: '" + args.get(i) + "'");
                    }
                }
                Task task = taskService.createTask(projectId, title, desc, priority, estimate, dueDate);
                out.println("Task created: " + task.getId() + " - " + task.getTitle());
            }
            case "update" -> {
                if (args.size() < 4) {
                    err.println("Usage: task update <task-id> <--title|--description|--priority|--estimate|--due> <value> [...]");
                    return;
                }
                UUID id = TaskPulseCli.parseUuid(args.get(1));
                Task existing = taskService.getTask(id);
                String title = existing.getTitle();
                String description = existing.getDescription();
                Priority priority = existing.getPriority();
                Integer estimate = existing.getEstimatedMinutes();
                LocalDate dueDate = existing.getDueDate();
                for (int i = 2; i < args.size(); i += 2) {
                    String option = args.get(i).toLowerCase(Locale.ROOT);
                    if (!List.of("--title", "--description", "--priority", "--estimate", "--due").contains(option)) {
                        throw new IllegalArgumentException("Unknown task update option: '" + args.get(i) + "'");
                    }
                    if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
                        throw new IllegalArgumentException("Missing value for " + option);
                    }
                    String value = args.get(i + 1);
                    switch (option) {
                        case "--title" -> title = value;
                        case "--description" -> description = value;
                        case "--priority" -> priority = parsePriority(value);
                        case "--estimate" -> estimate = parseEstimate(value);
                        case "--due" -> dueDate = value.equalsIgnoreCase("none") ? null : parseDate(value);
                        default -> throw new IllegalStateException("Unexpected option: " + option);
                    }
                }
                Task updated = taskService.updateTask(id, title, description, priority, estimate, dueDate);
                out.println("Task updated: " + updated.getId() + " - " + updated.getTitle());
            }
            case "list" -> {
                CliOptions options = CliOptions.parse(args, 1);
                TaskQuery.Builder qb = TaskQuery.builder();
                LocalDate fromDate = null;
                LocalDate toDate = null;
                if (options.has("--project")) {
                    qb.projectId(TaskPulseCli.parseUuid(options.get("--project")));
                }
                if (options.has("--state")) {
                    qb.state(parseState(options.get("--state")));
                }
                if (options.has("--priority")) {
                    qb.priority(parsePriority(options.get("--priority")));
                }
                if (options.has("--label")) {
                    qb.label(options.get("--label"));
                }
                if (options.has("--search")) {
                    qb.textSearch(options.get("--search"));
                }
                if (options.has("--overdue")) {
                    qb.overdue(true);
                }
                if (options.has("--exceeded")) {
                    String threshold = options.get("--exceeded");
                    qb.varianceThresholdMinutes(threshold.isEmpty() ? 0 : parseVarianceThreshold(threshold));
                }
                if (options.has("--sort")) {
                    qb.sortOrder(parseSortOrder(options.get("--sort")));
                }
                if (options.has("--from")) {
                    fromDate = parseDate(options.get("--from"));
                }
                if (options.has("--to")) {
                    toDate = parseDate(options.get("--to"));
                }
                qb.dueDateRange(fromDate, toDate);
                TaskQuery query = qb.build();
                List<Task> tasks = taskService.findTasks(query);
                if (tasks.isEmpty()) {
                    out.println("No tasks found.");
                } else {
                    for (Task t : tasks) {
                        String alert = query.varianceThresholdMinutes() == null ? ""
                                : " | " + t.getVarianceMinutes() + " min over estimate";
                        out.println(t.getId() + " | " + t.getState() + " | " + t.getPriority() + " | " + t.getTitle() + alert);
                    }
                }
            }
            case "show" -> {
                if (args.size() < 2) {
                    err.println("Usage: task show <task-id>");
                    return;
                }
                UUID id = TaskPulseCli.parseUuid(args.get(1));
                Task t = taskService.getTask(id);
                out.println("ID:          " + t.getId());
                out.println("Project:     " + t.getProjectId());
                out.println("Title:       " + t.getTitle());
                out.println("Description: " + t.getDescription());
                out.println("State:       " + t.getState());
                out.println("Priority:    " + t.getPriority());
                out.println("Estimate:    " + (t.getEstimatedMinutes() == null ? "not estimated" : t.getEstimatedMinutes() + " min"));
                out.println("Due date:    " + (t.getDueDate() == null ? "none" : t.getDueDate()));
                out.println("Labels:      " + (t.getLabels().isEmpty() ? "none" : String.join(", ", t.getLabels())));
                out.println("Worklogs:    " + t.getWorklogs().size() + " (" + t.getTotalRecordedMinutes() + " min recorded)");
                Long variance = t.getVarianceMinutes();
                out.println("Variance:    " + (variance == null ? "unavailable" : variance + " min"));
            }
            case "transition" -> {
                if (args.size() < 3) {
                    err.println("Usage: task transition <task-id> <state>");
                    return;
                }
                UUID id = TaskPulseCli.parseUuid(args.get(1));
                TaskState nextState = parseState(args.get(2));
                Task t = taskService.transitionTask(id, nextState);
                out.println("Task " + t.getId() + " transitioned to " + t.getState());
            }
            case "label" -> {
                if (args.size() < 4) {
                    err.println("Usage: task label <add|remove> <task-id> <label>");
                    return;
                }
                String sub = args.get(1).toLowerCase(Locale.ROOT);
                UUID id = TaskPulseCli.parseUuid(args.get(2));
                String label = args.get(3);
                if ("add".equals(sub)) {
                    taskService.addLabel(id, label);
                    out.println("Label '" + label + "' added to task " + id);
                } else if ("remove".equals(sub)) {
                    taskService.removeLabel(id, label);
                    out.println("Label '" + label + "' removed from task " + id);
                } else {
                    err.println("Unknown label action: " + sub);
                }
            }
            case "delete" -> {
                if (args.size() < 2) {
                    err.println("Usage: task delete <task-id>");
                    return;
                }
                UUID id = TaskPulseCli.parseUuid(args.get(1));
                taskService.deleteTask(id);
                out.println("Task deleted: " + id);
            }
            
            default -> err.println("Unknown task action: '" + action + "'");
        }
    }

    private long parseVarianceThreshold(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--exceeded expects a whole number of minutes, got '" + value + "'");
        }
    }
    private static String optionValue(List<String> args, int optionIndex, String option) {
        if (optionIndex + 1 >= args.size() || args.get(optionIndex + 1).startsWith("--")) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args.get(optionIndex + 1);
    }

    private Integer parseEstimate(String value) {
        if (value.equalsIgnoreCase("none")) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid estimate: '" + value + "'. Expected whole minutes or none");
        }
    }

    private LocalDate parseDate(String val) {
        try {
            return LocalDate.parse(val.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: '" + val + "'. Expected format: YYYY-MM-DD");
        }
    }

    private TaskState parseState(String val) {
        try {
            return TaskState.valueOf(val.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid task state: '" + val + "'. Allowed: " + Arrays.toString(TaskState.values()));
        }
    }

    private Priority parsePriority(String val) {
        try {
            return Priority.valueOf(val.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid priority: '" + val + "'. Allowed: " + Arrays.toString(Priority.values()));
        }
    }

    private TaskSortOrder parseSortOrder(String val) {
        return switch (val.strip().toLowerCase(Locale.ROOT)) {
            case "title" -> TaskSortOrder.TITLE_ASC;
            case "priority" -> TaskSortOrder.PRIORITY_DESC;
            case "duedate" -> TaskSortOrder.DUE_DATE_ASC;
            case "priority-duedate" -> TaskSortOrder.PRIORITY_DESC_THEN_DUE_DATE_ASC;
            default -> throw new IllegalArgumentException("Invalid sort order: '" + val + "'. Allowed: [title, priority, duedate, priority-duedate]");
        };
    }
}
