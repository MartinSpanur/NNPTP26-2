package cz.upce.fei.nnptp.taskpulse.service;

import cz.upce.fei.nnptp.taskpulse.domain.Task;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

public final class TaskQueryEvaluator {
    private TaskQueryEvaluator() {
    }

    public static TaskSpecification toSpecification(TaskQuery query, LocalDate today) {
        Objects.requireNonNull(query, "Query must not be null");
        Objects.requireNonNull(today, "Today must not be null");

        TaskSpecification specification = task -> true;
        if (query.projectId() != null) {
            specification = specification.and(task -> query.projectId().equals(task.getProjectId()));
        }
        if (query.state() != null) {
            specification = specification.and(task -> query.state() == task.getState());
        }
        if (query.priority() != null) {
            specification = specification.and(task -> query.priority() == task.getPriority());
        }
        if (query.label() != null) {
            specification = specification.and(task -> task.hasLabel(query.label()));
        }
        if (query.textSearch() != null) {
            specification = specification.and(task -> containsText(task, query.textSearch()));
        }
        if (query.fromDueDate() != null || query.toDueDate() != null) {
            specification = specification.and(task -> withinDueDateRange(task, query));
        }
        if (query.overdue() != null) {
            specification = specification.and(task -> task.isOverdue(today) == query.overdue());
        }
        if (query.varianceThresholdMinutes() != null) {
            specification = specification.and(task -> {
                Long variance = task.getVarianceMinutes();
                return variance != null && variance > query.varianceThresholdMinutes();
            });
        }
        return specification;
    }

    private static boolean containsText(Task task, String text) {
        return task.getTitle().toLowerCase(Locale.ROOT).contains(text)
                || task.getDescription().toLowerCase(Locale.ROOT).contains(text);
    }

    private static boolean withinDueDateRange(Task task, TaskQuery query) {
        LocalDate dueDate = task.getDueDate();
        return dueDate != null
                && (query.fromDueDate() == null || !dueDate.isBefore(query.fromDueDate()))
                && (query.toDueDate() == null || !dueDate.isAfter(query.toDueDate()));
    }
}
