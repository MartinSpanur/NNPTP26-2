package cz.upce.fei.nnptp.taskpulse.service;

import cz.upce.fei.nnptp.taskpulse.domain.Task;

import java.util.Objects;

@FunctionalInterface
public interface TaskSpecification {
    boolean isSatisfiedBy(Task task);

    default TaskSpecification and(TaskSpecification other) {
        Objects.requireNonNull(other, "Other specification must not be null");
        return task -> isSatisfiedBy(task) && other.isSatisfiedBy(task);
    }

    default TaskSpecification or(TaskSpecification other) {
        Objects.requireNonNull(other, "Other specification must not be null");
        return task -> isSatisfiedBy(task) || other.isSatisfiedBy(task);
    }

    default TaskSpecification not() {
        return task -> !isSatisfiedBy(task);
    }
}
