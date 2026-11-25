package cz.upce.fei.nnptp.taskpulse.report;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Renders a project summary as Markdown (FR-08 / FEAT-01).
 */
public final class MarkdownReportExporter implements ReportExporter {

    static final String UNESTIMATED = "-";

    @Override
    public String export(Project project, List<Task> tasks, LocalDate asOfDate) {
        Objects.requireNonNull(project, "Project must not be null");
        Objects.requireNonNull(tasks, "Tasks must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        Map<TaskState, Long> countByState = new EnumMap<>(TaskState.class);
        for (TaskState state : TaskState.values()) {
            countByState.put(state, 0L);
        }

        long overdueCount = 0L;
        long totalRecordedMinutes = 0L;
        long totalEstimatedMinutes = 0L;
        long totalVarianceMinutes = 0L;
        for (Task task : tasks) {
            Objects.requireNonNull(task, "Task must not be null");
            countByState.merge(task.getState(), 1L, Long::sum);
            if (task.isOverdue(asOfDate)) {
                overdueCount++;
            }
            totalRecordedMinutes += task.getTotalRecordedMinutes();
            Long varianceMinutes = task.getVarianceMinutes();
            if (varianceMinutes != null) {
                totalEstimatedMinutes += task.getEstimatedMinutes();
                totalVarianceMinutes += varianceMinutes;
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# Project Summary: ").append(escapeInline(project.getName())).append('\n');
        md.append('\n');
        md.append("- **ID:** ").append(project.getId()).append('\n');
        md.append("- **Description:** ").append(escapeInline(project.getDescription())).append('\n');
        md.append('\n');
        md.append("## Metrics").append('\n');
        md.append('\n');
        md.append("| Metric | Value |").append('\n');
        md.append("| --- | --- |").append('\n');
        for (TaskState state : TaskState.values()) {
            md.append("| ").append(state.name()).append(" | ").append(countByState.get(state)).append(" |").append('\n');
        }
        md.append("| Overdue | ").append(overdueCount).append(" |").append('\n');
        md.append("| Total recorded minutes | ").append(totalRecordedMinutes).append(" |").append('\n');
        md.append("| Total estimated minutes | ").append(totalEstimatedMinutes).append(" |").append('\n');
        md.append("| Total variance minutes | ").append(totalVarianceMinutes).append(" |").append('\n');
        md.append('\n');
        md.append("## Tasks").append('\n');
        md.append('\n');
        md.append("| ID | Title | State | Priority | Due date | Estimate (min) | Recorded (min) | Variance (min) |")
                .append('\n');
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- |").append('\n');

        for (Task task : tasks) {
            md.append("| ")
                    .append(task.getId())
                    .append(" | ")
                    .append(escapeCell(task.getTitle()))
                    .append(" | ")
                    .append(task.getState().name())
                    .append(" | ")
                    .append(task.getPriority().name())
                    .append(" | ")
                    .append(formatDueDate(task.getDueDate()))
                    .append(" | ")
                    .append(formatEstimate(task.getEstimatedMinutes()))
                    .append(" | ")
                    .append(task.getTotalRecordedMinutes())
                    .append(" | ")
                    .append(formatVariance(task.getVarianceMinutes()))
                    .append(" |")
                    .append('\n');
        }

        return md.toString();
    }

    static String formatEstimate(Integer estimatedMinutes) {
        if (estimatedMinutes == null) {
            return UNESTIMATED;
        }
        return Integer.toString(estimatedMinutes);
    }

    /**
     * Formats the estimate variance of a single task; tasks without an estimate have no variance.
     */
    static String formatVariance(Long varianceMinutes) {
        if (varianceMinutes == null) {
            return UNESTIMATED;
        }
        return Long.toString(varianceMinutes);
    }

    static String formatDueDate(LocalDate dueDate) {
        if (dueDate == null) {
            return "-";
        }
        return dueDate.toString();
    }

    /**
     * Preserves literal HTML tags and entities in table cells and keeps pipes
     * and newlines from breaking the table structure.
     */
    static String escapeCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\r\n", " ")
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    static String escapeInline(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }
}
