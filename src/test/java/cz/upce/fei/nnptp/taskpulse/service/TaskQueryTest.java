package cz.upce.fei.nnptp.taskpulse.service;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskQueryTest {

    @Test
    void builder_WithDefaults_CreatesQueryWithTitleAscSortOrder() {
        TaskQuery query = TaskQuery.builder().build();

        // default query should only set default sort order
        assertThat(query.projectId()).isNull();
        assertThat(query.state()).isNull();
        assertThat(query.priority()).isNull();
        assertThat(query.label()).isNull();
        assertThat(query.textSearch()).isNull();
        assertThat(query.fromDueDate()).isNull();
        assertThat(query.toDueDate()).isNull();
        assertThat(query.overdue()).isNull();
        assertThat(query.sortOrder()).isEqualTo(TaskSortOrder.TITLE_ASC);
        assertThat(query.varianceThresholdMinutes()).isNull();
    }

    @Test
    void builder_WithAllFields_SetsFieldsCorrectly() {
        UUID projectId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 1, 10);
        LocalDate to = LocalDate.of(2026, 1, 20);

        TaskQuery query = TaskQuery.builder()
                .projectId(projectId)
                .state(TaskState.IN_PROGRESS)
                .priority(Priority.HIGH)
                .label("  BACKEND  ")
                .textSearch("  Refactor Code  ")
                .dueDateRange(from, to)
                .overdue(true)
                .sortOrder(TaskSortOrder.DUE_DATE_ASC)
                .varianceThresholdMinutes(45)
                .build();

        assertThat(query.projectId()).isEqualTo(projectId);
        assertThat(query.state()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(query.priority()).isEqualTo(Priority.HIGH);
        assertThat(query.label()).isEqualTo("backend");
        assertThat(query.textSearch()).isEqualTo("refactor code");
        assertThat(query.fromDueDate()).isEqualTo(from);
        assertThat(query.toDueDate()).isEqualTo(to);
        assertThat(query.overdue()).isTrue();
        assertThat(query.sortOrder()).isEqualTo(TaskSortOrder.DUE_DATE_ASC);
        assertThat(query.varianceThresholdMinutes()).isEqualTo(45L);
    }

    @Test
    void constructor_NineParameters_SetsVarianceThresholdToNull() {
        UUID projectId = UUID.randomUUID();
        LocalDate d = LocalDate.of(2026, 3, 1);

        TaskQuery query = new TaskQuery(
                projectId,
                TaskState.DONE,
                Priority.LOW,
                "docs",
                "manual",
                d,
                d,
                false,
                TaskSortOrder.PRIORITY_DESC
        );

        assertThat(query.projectId()).isEqualTo(projectId);
        assertThat(query.state()).isEqualTo(TaskState.DONE);
        assertThat(query.priority()).isEqualTo(Priority.LOW);
        assertThat(query.label()).isEqualTo("docs");
        assertThat(query.textSearch()).isEqualTo("manual");
        assertThat(query.fromDueDate()).isEqualTo(d);
        assertThat(query.toDueDate()).isEqualTo(d);
        assertThat(query.overdue()).isFalse();
        assertThat(query.sortOrder()).isEqualTo(TaskSortOrder.PRIORITY_DESC);
        assertThat(query.varianceThresholdMinutes()).isNull();
    }

    @Test
    void constructor_WhenFromDateAfterToDate_ThrowsValidationException() {
        LocalDate from = LocalDate.of(2026, 5, 20);
        LocalDate to = LocalDate.of(2026, 5, 10);

        // invalid date interval should fail validation
        assertThatThrownBy(() -> TaskQuery.builder().dueDateRange(from, to).build())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid date range: fromDate (2026-05-20) is after toDate (2026-05-10)");
    }

    @Test
    void constructor_WhenFromDateEqualsOrBeforeToDate_DoesNotThrow() {
        LocalDate d = LocalDate.of(2026, 5, 15);

        TaskQuery sameDay = TaskQuery.builder().dueDateRange(d, d).build();
        assertThat(sameDay.fromDueDate()).isEqualTo(d);
        assertThat(sameDay.toDueDate()).isEqualTo(d);

        TaskQuery singleBoundFrom = TaskQuery.builder().dueDateRange(d, null).build();
        assertThat(singleBoundFrom.fromDueDate()).isEqualTo(d);
        assertThat(singleBoundFrom.toDueDate()).isNull();

        TaskQuery singleBoundTo = TaskQuery.builder().dueDateRange(null, d).build();
        assertThat(singleBoundTo.fromDueDate()).isNull();
        assertThat(singleBoundTo.toDueDate()).isEqualTo(d);
    }

    @Test
    void constructor_WhenVarianceThresholdNegative_ThrowsValidationException() {
        assertThatThrownBy(() -> TaskQuery.builder().varianceThresholdMinutes(-5).build())
                .isInstanceOf(ValidationException.class)
                .hasMessage("Variance threshold must be a non-negative number of minutes");
    }

    @Test
    void constructor_WhenVarianceThresholdZeroOrPositive_SetsThreshold() {
        TaskQuery zeroThreshold = TaskQuery.builder().varianceThresholdMinutes(0).build();
        assertThat(zeroThreshold.varianceThresholdMinutes()).isEqualTo(0L);

        TaskQuery positiveThreshold = TaskQuery.builder().varianceThresholdMinutes(120).build();
        assertThat(positiveThreshold.varianceThresholdMinutes()).isEqualTo(120L);
    }

    @Test
    void constructor_LabelNormalization_TrimsAndLowercasesOrSetsNull() {
        TaskQuery trimmed = TaskQuery.builder().label("  URGENT  ").build();
        assertThat(trimmed.label()).isEqualTo("urgent");

        TaskQuery nullLabel = TaskQuery.builder().label(null).build();
        assertThat(nullLabel.label()).isNull();

        TaskQuery emptyLabel = TaskQuery.builder().label("").build();
        assertThat(emptyLabel.label()).isNull();

        TaskQuery blankLabel = TaskQuery.builder().label("   ").build();
        assertThat(blankLabel.label()).isNull();
    }

    @Test
    void constructor_LabelWithSeparator_ThrowsValidationException() {
        // label with semicolon should be rejected by label normalizer
        assertThatThrownBy(() -> TaskQuery.builder().label("bug;feature").build())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Label must not contain ';'");
    }

    @Test
    void constructor_TextSearchNormalization_TrimsAndLowercasesOrSetsNull() {
        TaskQuery query = TaskQuery.builder().textSearch("  Search Text Here  ").build();
        assertThat(query.textSearch()).isEqualTo("search text here");

        TaskQuery nullSearch = TaskQuery.builder().textSearch(null).build();
        assertThat(nullSearch.textSearch()).isNull();

        TaskQuery emptySearch = TaskQuery.builder().textSearch("").build();
        assertThat(emptySearch.textSearch()).isNull();

        TaskQuery blankSearch = TaskQuery.builder().textSearch("   \t ").build();
        assertThat(blankSearch.textSearch()).isNull();
    }

    @Test
    void constructor_WhenSortOrderNull_DefaultsToTitleAsc() {
        TaskQuery query = new TaskQuery(null, null, null, null, null, null, null, null, null, null);
        assertThat(query.sortOrder()).isEqualTo(TaskSortOrder.TITLE_ASC);

        TaskQuery queryWithCustomSort = TaskQuery.builder().sortOrder(TaskSortOrder.PRIORITY_DESC_THEN_DUE_DATE_ASC).build();
        assertThat(queryWithCustomSort.sortOrder()).isEqualTo(TaskSortOrder.PRIORITY_DESC_THEN_DUE_DATE_ASC);
    }
}
