package cz.upce.fei.nnptp.taskpulse.report;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskBuilder;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonReportExporterTest {

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 11, 4);
    private final ReportExporter exporter = new JsonReportExporter();

    @Test
    void emptyProjectProducesZeroMetricsAndAnEmptyTaskArray() {
        Project project = new Project("Empty");

        String report = exporter.export(project, List.of(), AS_OF_DATE);

        assertThat(report).isEqualTo("""
                {
                  "project": {"id": "%s", "name": "Empty", "description": ""},
                  "metrics": {
                    "taskCountsByState": {"TODO": 0, "IN_PROGRESS": 0, "BLOCKED": 0, "DONE": 0},
                    "overdueCount": 0,
                    "totalRecordedMinutes": 0,
                    "totalEstimatedMinutes": 0,
                    "totalVarianceMinutes": 0
                  },
                  "tasks": []
                }
                """.formatted(project.getId()));
    }

    @Test
    void exportsTaskDetailsAndMetricsUsingTheSuppliedDate() {
        Project project = new Project("Coursework", "Project report");
        LocalDate pastDue = AS_OF_DATE.minusDays(1);
        Worklog finishedWork = new Worklog(pastDue.atStartOfDay(), 2_500_000_000L, "dev");
        Worklog overdueWork = new Worklog(pastDue.atTime(9, 0), 30, "dev");

        Task dueToday = new TaskBuilder(project.getId(), "Due today").dueDate(AS_OF_DATE).build();
        Task finished = new TaskBuilder(project.getId(), "Finished")
                .state(TaskState.DONE).dueDate(pastDue).estimatedMinutes(0)
                .worklogs(List.of(finishedWork)).build();
        Task overdue = new TaskBuilder(project.getId(), "Overdue")
                .state(TaskState.IN_PROGRESS).priority(Priority.CRITICAL)
                .dueDate(pastDue).estimatedMinutes(120)
                .worklogs(List.of(overdueWork)).build();

        String report = exporter.export(project, List.of(dueToday, finished, overdue), AS_OF_DATE);

        assertThat(report).isEqualTo("""
                {
                  "project": {"id": "%s", "name": "Coursework", "description": "Project report"},
                  "metrics": {
                    "taskCountsByState": {"TODO": 1, "IN_PROGRESS": 1, "BLOCKED": 0, "DONE": 1},
                    "overdueCount": 1,
                    "totalRecordedMinutes": 2500000030,
                    "totalEstimatedMinutes": 120,
                    "totalVarianceMinutes": 2499999910
                  },
                  "tasks": [
                    {"id": "%s", "title": "Due today", "state": "TODO", "priority": "MEDIUM", \
                "dueDate": "2026-11-04", "estimatedMinutes": null, "recordedMinutes": 0, "varianceMinutes": null},
                    {"id": "%s", "title": "Finished", "state": "DONE", "priority": "MEDIUM", \
                "dueDate": "2026-11-03", "estimatedMinutes": 0, \
                "recordedMinutes": 2500000000, "varianceMinutes": 2500000000},
                    {"id": "%s", "title": "Overdue", "state": "IN_PROGRESS", "priority": "CRITICAL", \
                "dueDate": "2026-11-03", "estimatedMinutes": 120, "recordedMinutes": 30, "varianceMinutes": -90}
                  ]
                }
                """.formatted(project.getId(), dueToday.getId(), finished.getId(), overdue.getId()));
        assertThat(finished.getWorklogs()).containsExactly(finishedWork);
    }

    @Test
    void unestimatedTasksDoNotContributeToVarianceTotals() {
        Project project = new Project("Unestimated only");
        Worklog work = new Worklog(AS_OF_DATE.atStartOfDay(), 90, "dev");
        Task unestimated = new TaskBuilder(project.getId(), "No estimate")
                .state(TaskState.IN_PROGRESS).worklogs(List.of(work)).build();

        String report = exporter.export(project, List.of(unestimated), AS_OF_DATE);

        assertThat(report).contains("\"totalRecordedMinutes\": 90");
        assertThat(report).contains("\"totalEstimatedMinutes\": 0");
        assertThat(report).contains("\"totalVarianceMinutes\": 0");
        assertThat(report).contains("\"varianceMinutes\": null");
    }

    @Test
    void escapesUserTextWithoutChangingTheDomainObjects() {
        String original = "Příliš \"žluťoučký\" \\ kůň\n\t\037 🐎";
        String escaped = "\"Příliš \\\"žluťoučký\\\" \\\\ kůň\\u000a\\u0009\\u001f \\ud83d\\udc0e\"";
        Project project = new Project(original, original);
        Task task = new Task(project.getId(), original);

        String report = exporter.export(project, List.of(task), AS_OF_DATE);

        assertThat(report).contains("\"name\": " + escaped, "\"description\": " + escaped,
                "\"title\": " + escaped, "\"dueDate\": null");
        assertThat(project.getName()).isEqualTo(original);
        assertThat(project.getDescription()).isEqualTo(original);
        assertThat(task.getTitle()).isEqualTo(original);
    }
}
