package cz.upce.fei.nnptp.taskpulse.service;

import cz.upce.fei.nnptp.taskpulse.domain.Label;
import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public record TaskQuery(
        UUID projectId,
        TaskState state,
        Priority priority,
        String label,
        String textSearch,
        LocalDate fromDueDate,
        LocalDate toDueDate,
        Boolean overdue,
        TaskSortOrder sortOrder,
        Long varianceThresholdMinutes
) {
    public TaskQuery {
        if (fromDueDate != null && toDueDate != null && fromDueDate.isAfter(toDueDate)) {
            throw new ValidationException("Invalid date range: fromDate (" + fromDueDate + ") is after toDate (" + toDueDate + ")");
        }
        if (varianceThresholdMinutes != null && varianceThresholdMinutes < 0) {
            throw new ValidationException("Variance threshold must be a non-negative number of minutes");
        }
        if (label != null && !label.isBlank()) {
            label = Label.normalize(label);
        } else {
            label = null;
        }
        textSearch = (textSearch != null && !textSearch.isBlank()) ? textSearch.strip().toLowerCase(Locale.ROOT) : null;
        sortOrder = sortOrder == null ? TaskSortOrder.TITLE_ASC : sortOrder;
    }

    public TaskQuery(UUID projectId, TaskState state, Priority priority, String label, String textSearch,
                     LocalDate fromDueDate, LocalDate toDueDate, Boolean overdue, TaskSortOrder sortOrder) {
        this(projectId, state, priority, label, textSearch, fromDueDate, toDueDate, overdue, sortOrder, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID projectId;
        private TaskState state;
        private Priority priority;
        private String label;
        private String textSearch;
        private LocalDate fromDueDate;
        private LocalDate toDueDate;
        private Boolean overdue;
        private TaskSortOrder sortOrder = TaskSortOrder.TITLE_ASC;
        private Long varianceThresholdMinutes;

        public Builder projectId(UUID projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder state(TaskState state) {
            this.state = state;
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder textSearch(String textSearch) {
            this.textSearch = textSearch;
            return this;
        }

        public Builder dueDateRange(LocalDate fromDate, LocalDate toDate) {
            this.fromDueDate = fromDate;
            this.toDueDate = toDate;
            return this;
        }

        public Builder overdue(Boolean overdue) {
            this.overdue = overdue;
            return this;
        }

        public Builder sortOrder(TaskSortOrder sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public Builder varianceThresholdMinutes(long minutes) {
            this.varianceThresholdMinutes = minutes;
            return this;
        }

        public TaskQuery build() {
            return new TaskQuery(projectId, state, priority, label, textSearch, fromDueDate, toDueDate,
                    overdue, sortOrder, varianceThresholdMinutes);
        }
    }
}
