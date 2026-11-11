package cz.upce.fei.nnptp.taskpulse.report;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class JsonReportExporter implements ReportExporter {

    @Override
    public String export(Project project, List<Task> tasks, LocalDate asOfDate) {
        Objects.requireNonNull(project, "Project must not be null");
        Objects.requireNonNull(tasks, "Tasks must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        Map<TaskState, Long> counts = new EnumMap<>(TaskState.class);
        StringJoiner taskRows = new StringJoiner(",\n    ", "[\n    ", "\n  ]").setEmptyValue("[]");
        long overdueCount = 0;
        long totalRecordedMinutes = 0;
        for (Task task : tasks) {
            Objects.requireNonNull(task, "Task must not be null");
            counts.merge(task.getState(), 1L, Long::sum);
            if (task.isOverdue(asOfDate)) {
                overdueCount++;
            }
            long recordedMinutes = task.getTotalRecordedMinutes();
            totalRecordedMinutes += recordedMinutes;
            taskRows.add(renderTask(task, recordedMinutes));
        }

        StringJoiner stateCounts = new StringJoiner(", ", "{", "}");
        for (TaskState state : TaskState.values()) {
            stateCounts.add(quote(state.name()) + ": " + counts.getOrDefault(state, 0L));
        }

        return """
                {
                  "project": {"id": "%s", "name": %s, "description": %s},
                  "metrics": {
                    "taskCountsByState": %s,
                    "overdueCount": %s,
                    "totalRecordedMinutes": %s
                  },
                  "tasks": %s
                }
                """.formatted(project.getId(), quote(project.getName()), quote(project.getDescription()),
                stateCounts, overdueCount, totalRecordedMinutes, taskRows);
    }

    private static String renderTask(Task task, long recordedMinutes) {
        LocalDate dueDate = task.getDueDate();
        return """
                {"id": "%s", "title": %s, "state": "%s", "priority": "%s", \
                "dueDate": %s, "estimatedMinutes": %s, "recordedMinutes": %s, "varianceMinutes": %s}"""
                .formatted(task.getId(), quote(task.getTitle()), task.getState().name(), task.getPriority().name(),
                        dueDate == null ? "null" : quote(dueDate.toString()), task.getEstimatedMinutes(),
                        recordedMinutes, task.getVarianceMinutes());
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder().append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                quoted.append('\\').append(character);
            } else if (character < ' ' || Character.isSurrogate(character)) {
                quoted.append("\\u").append(HexFormat.of().toHexDigits(character));
            } else {
                quoted.append(character);
            }
        }
        return quoted.append('"').toString();
    }
}
