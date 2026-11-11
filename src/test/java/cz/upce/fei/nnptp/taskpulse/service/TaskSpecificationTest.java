package cz.upce.fei.nnptp.taskpulse.service;

import cz.upce.fei.nnptp.taskpulse.domain.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskSpecificationTest {
    private final Task task = new Task(UUID.randomUUID(), "Example task");

    @ParameterizedTest
    @CsvSource({
            "false, false, false, false",
            "false, true, false, true",
            "true, false, false, true",
            "true, true, true, true"
    })
    void combinesSpecifications(boolean left, boolean right, boolean expectedAnd, boolean expectedOr) {
        TaskSpecification first = candidate -> left;
        TaskSpecification second = candidate -> right;

        assertThat(first.and(second).isSatisfiedBy(task)).isEqualTo(expectedAnd);
        assertThat(first.or(second).isSatisfiedBy(task)).isEqualTo(expectedOr);
        assertThat(first.not().isSatisfiedBy(task)).isEqualTo(!left);
    }

    @Test
    void andSkipsRightSpecificationAfterFalse() {
        TaskSpecification rejected = candidate -> false;
        TaskSpecification unexpected = candidate -> {
            throw new AssertionError("Right specification should not be evaluated");
        };
        assertThat(rejected.and(unexpected).isSatisfiedBy(task)).isFalse();
    }

    @Test
    void orSkipsRightSpecificationAfterTrue() {
        TaskSpecification accepted = candidate -> true;
        TaskSpecification unexpected = candidate -> {
            throw new AssertionError("Right specification should not be evaluated");
        };
        assertThat(accepted.or(unexpected).isSatisfiedBy(task)).isTrue();
    }

    @Test
    void rejectsNullOperandsWhenComposing() {
        TaskSpecification accepted = candidate -> true;
        assertThatThrownBy(() -> accepted.and(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> accepted.or(null)).isInstanceOf(NullPointerException.class);
    }
}
