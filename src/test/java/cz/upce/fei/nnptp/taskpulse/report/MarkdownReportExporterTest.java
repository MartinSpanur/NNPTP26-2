package cz.upce.fei.nnptp.taskpulse.report;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownReportExporterTest {

    private MarkdownReportExporter exporter;
    private LocalDate asOfDate;

    @BeforeEach
    void setUp() {
        exporter = new MarkdownReportExporter();
        asOfDate = LocalDate.of(2026, 11, 4);
    }

    @Test
    void emptyProjectProducesValidReportWithZeroTotals() {
        UUID projectId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Project project = new Project(projectId, "Empty", "No tasks yet");

        String report = exporter.export(project, List.of(), asOfDate);

        assertThat(report).contains("# Project Summary: Empty");
        assertThat(report).contains("- **ID:** 11111111-1111-1111-1111-111111111111");
        assertThat(report).contains("| TODO | 0 |");
        assertThat(report).contains("| IN_PROGRESS | 0 |");
        assertThat(report).contains("| BLOCKED | 0 |");
        assertThat(report).contains("| DONE | 0 |");
        assertThat(report).contains("| Overdue | 0 |");
        assertThat(report).contains("| Total recorded minutes | 0 |");
        assertThat(report).contains("| Total estimated minutes | 0 |");
        assertThat(report).contains("| Total variance minutes | 0 |");
        assertThat(report).contains(
                "| ID | Title | State | Priority | Due date | Estimate (min) | Recorded (min) | Variance (min) |");
        assertThat(countDataRows(report)).isZero();
    }

    @Test
    void reportContainsProjectMetricsAndTaskDetails() {
        UUID projectId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Project project = new Project(projectId, "Coursework", "NNPTP project");

        UUID todoId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID doneId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID overdueId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        Task todo = new Task(todoId, projectId, "Write tests", "unit", Priority.HIGH, TaskState.TODO,
                null, LocalDate.of(2026, 11, 10), null, null);
        Task done = new Task(doneId, projectId, "Domain model", "", Priority.MEDIUM, TaskState.DONE,
                0, LocalDate.of(2026, 10, 1), null, null);
        done.restoreWorklog(new Worklog(LocalDateTime.of(2026, 10, 1, 12, 0), 45, "dev", "impl"));
        done.restoreWorklog(new Worklog(LocalDateTime.of(2026, 10, 1, 13, 0), 15, "dev", "tests"));

        Task overdue = new Task(overdueId, projectId, "Fix CSV", "", Priority.CRITICAL, TaskState.IN_PROGRESS,
                120, LocalDate.of(2026, 11, 1), null, null);
        overdue.restoreWorklog(new Worklog(LocalDateTime.of(2026, 11, 2, 9, 0), 30, "dev", "wip"));

        String report = exporter.export(project, List.of(todo, done, overdue), asOfDate);

        assertThat(report).contains("# Project Summary: Coursework");
        assertThat(report).contains("- **ID:** 22222222-2222-2222-2222-222222222222");
        assertThat(report).contains("- **Description:** NNPTP project");
        assertThat(report).contains("| TODO | 1 |");
        assertThat(report).contains("| IN_PROGRESS | 1 |");
        assertThat(report).contains("| BLOCKED | 0 |");
        assertThat(report).contains("| DONE | 1 |");
        assertThat(report).contains("| Overdue | 1 |");
        assertThat(report).contains("| Total recorded minutes | 90 |");
        assertThat(report).contains("| Total estimated minutes | 120 |");
        assertThat(report).contains("| Total variance minutes | -30 |");

        assertThat(report).contains("| aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa | Write tests | TODO | HIGH | 2026-11-10 | - | 0 | - |");
        assertThat(report).contains("| bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb | Domain model | DONE | MEDIUM | 2026-10-01 | 0 | 60 | 60 |");
        assertThat(report).contains("| cccccccc-cccc-cccc-cccc-cccccccccccc | Fix CSV | IN_PROGRESS | CRITICAL | 2026-11-01 | 120 | 30 | -90 |");
    }

    @Test
    void unestimatedTasksAreDistinguishableFromZeroEstimate() {
        assertThat(MarkdownReportExporter.formatEstimate(null)).isEqualTo("-");
        assertThat(MarkdownReportExporter.formatEstimate(0)).isEqualTo("0");
        assertThat(MarkdownReportExporter.formatEstimate(45)).isEqualTo("45");
    }

    @Test
    void unestimatedTasksAreExcludedFromVarianceTotals() {
        UUID projectId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID taskId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        Project project = new Project(projectId, "Unestimated only", "");
        Task unestimated = new Task(taskId, projectId, "No estimate", "", Priority.MEDIUM, TaskState.IN_PROGRESS,
                null, null, null, null);
        unestimated.restoreWorklog(new Worklog(LocalDateTime.of(2026, 11, 3, 8, 0), 90, "dev", ""));

        String report = exporter.export(project, List.of(unestimated), asOfDate);

        assertThat(report).contains("| Total recorded minutes | 90 |");
        assertThat(report).contains("| Total estimated minutes | 0 |");
        assertThat(report).contains("| Total variance minutes | 0 |");
        assertThat(report).contains("| dddddddd-dddd-dddd-dddd-dddddddddddd | No estimate | IN_PROGRESS | MEDIUM | - | - | 90 | - |");
    }

    @Test
    void varianceDistinguishesMissingEstimateFromZeroVariance() {
        assertThat(MarkdownReportExporter.formatVariance(null)).isEqualTo("-");
        assertThat(MarkdownReportExporter.formatVariance(0L)).isEqualTo("0");
        assertThat(MarkdownReportExporter.formatVariance(-90L)).isEqualTo("-90");
    }

    @Test
    void markdownTablesEscapePipesAndNewlinesInTitles() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project(projectId, "A|B\nC", "desc");
        Task task = new Task(UUID.randomUUID(), projectId, "Title | with\npipe", "", Priority.LOW, TaskState.TODO,
                null, null, null, null);

        String report = exporter.export(project, List.of(task), asOfDate);

        assertThat(report).contains("# Project Summary: A|B C");
        assertThat(report).contains("Title \\| with pipe");
        assertThat(report).doesNotContain("| Title | with");
    }

    @Test
    void escapesHtmlTagsInTaskTitles() {
        Project project = new Project("Course");
        Task task = new Task(project.getId(), "Fix <div> layout");

        String report = exporter.export(project, List.of(task), asOfDate);

        assertThat(report).contains("| " + task.getId() + " | Fix &lt;div&gt; layout |");
    }

    @Test
    void preservesLiteralEntityReferencesInTaskTitles() {
        Project project = new Project("Course");
        Task task = new Task(project.getId(), "Document &lt; in HTML");

        String report = exporter.export(project, List.of(task), asOfDate);

        assertThat(report).contains("| " + task.getId() + " | Document &amp;lt; in HTML |");
    }

    @Test
    void exportDoesNotMutateProjectOrTasks() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project(projectId, "Stable", "unchanged");
        Task task = new Task(UUID.randomUUID(), projectId, "T1", "d", Priority.LOW, TaskState.TODO,
                10, LocalDate.of(2026, 12, 1), List.of("label"), null);
        String nameBefore = project.getName();
        String titleBefore = task.getTitle();
        TaskState stateBefore = task.getState();
        int labelCountBefore = task.getLabels().size();
        long recordedBefore = task.getTotalRecordedMinutes();

        exporter.export(project, List.of(task), asOfDate);

        assertThat(project.getName()).isEqualTo(nameBefore);
        assertThat(project.getDescription()).isEqualTo("unchanged");
        assertThat(task.getTitle()).isEqualTo(titleBefore);
        assertThat(task.getState()).isEqualTo(stateBefore);
        assertThat(task.getLabels()).hasSize(labelCountBefore);
        assertThat(task.getTotalRecordedMinutes()).isEqualTo(recordedBefore);
    }

    @Test
    void rejectNullArguments() {
        Project project = new Project("P", "");
        assertThatThrownBy(() -> exporter.export(null, List.of(), asOfDate))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> exporter.export(project, null, asOfDate))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> exporter.export(project, List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void escapeCellHandlesNullAndSpecialCharacters() {
        assertThat(MarkdownReportExporter.escapeCell(null)).isEmpty();
        assertThat(MarkdownReportExporter.escapeCell("")).isEmpty();
        assertThat(MarkdownReportExporter.escapeCell("a|b")).isEqualTo("a\\|b");
        assertThat(MarkdownReportExporter.escapeCell("a\\b")).isEqualTo("a\\\\b");
        assertThat(MarkdownReportExporter.escapeCell("line1\nline2")).isEqualTo("line1 line2");
    }

    private static long countDataRows(String report) {
        String tasksSection = report.substring(report.indexOf("## Tasks"));
        return tasksSection.lines()
                .filter(line -> line.startsWith("| "))
                .filter(line -> !line.startsWith("| ID |"))
                .filter(line -> !line.startsWith("| ---"))
                .count();
    }
}
