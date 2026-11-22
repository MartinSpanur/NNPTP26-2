package cz.upce.fei.nnptp.taskpulse.storage;

import cz.upce.fei.nnptp.taskpulse.domain.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTaskRepositoryTest {

    private InMemoryTaskRepository repository;
    private UUID projectId1;
    private UUID projectId2;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTaskRepository();
        projectId1 = UUID.randomUUID();
        projectId2 = UUID.randomUUID();
    }

    @Test
    void saveStoresNewTaskAndReturnsIt() {
        Task task = new Task(projectId1, "Test Task");

        Task saved = repository.save(task);

        assertThat(saved).isSameAs(task);
        assertThat(repository.findById(task.getId())).contains(task);
    }

    @Test
    void saveUpdatesExistingTask() {
        Task task = new Task(projectId1, "Original Title");
        repository.save(task);

        task.setTitle("Updated Title");
        repository.save(task);

        Optional<Task> retrieved = repository.findById(task.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getTitle()).isEqualTo("Updated Title");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void saveThrowsExceptionWhenTaskIsNull() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Task must not be null");
    }

    @Test
    void findByIdReturnsEmptyWhenTaskDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();

        Optional<Task> result = repository.findById(nonExistentId);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllReturnsEmptyListWhenNoTasksExist() {
        List<Task> all = repository.findAll();

        assertThat(all).isEmpty();
    }

    @Test
    void findAllReturnsAllStoredTasks() {
        Task task1 = new Task(projectId1, "Task 1");
        Task task2 = new Task(projectId2, "Task 2");
        repository.save(task1);
        repository.save(task2);

        List<Task> all = repository.findAll();

        assertThat(all).containsExactly(task1, task2);
    }

    @Test
    void findAllReturnsUnmodifiableList() {
        Task task = new Task(projectId1, "Task 1");
        repository.save(task);

        List<Task> all = repository.findAll();

        assertThatThrownBy(() -> all.add(new Task(projectId1, "Task 2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findByProjectIdReturnsOnlyMatchingTasks() {
        Task task1 = new Task(projectId1, "Task 1");
        Task task2 = new Task(projectId1, "Task 2");
        Task task3 = new Task(projectId2, "Task 3");
        repository.save(task1);
        repository.save(task2);
        repository.save(task3);

        List<Task> project1Tasks = repository.findByProjectId(projectId1);

        assertThat(project1Tasks).containsExactly(task1, task2);
    }

    @Test
    void findByProjectIdReturnsEmptyListWhenProjectHasNoTasks() {
        List<Task> tasks = repository.findByProjectId(UUID.randomUUID());

        assertThat(tasks).isEmpty();
    }

    @Test
    void findByProjectIdThrowsExceptionWhenProjectIdIsNull() {
        assertThatThrownBy(() -> repository.findByProjectId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Project ID must not be null");
    }

    @Test
    void existsByIdReturnsTrueWhenPresentAndFalseWhenAbsent() {
        Task task = new Task(projectId1, "Task");
        repository.save(task);

        assertThat(repository.existsById(task.getId())).isTrue();
        assertThat(repository.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void countByProjectIdReturnsCorrectCount() {
        Task task1 = new Task(projectId1, "Task 1");
        Task task2 = new Task(projectId1, "Task 2");
        Task task3 = new Task(projectId2, "Task 3");
        repository.save(task1);
        repository.save(task2);
        repository.save(task3);

        assertThat(repository.countByProjectId(projectId1)).isEqualTo(2);
        assertThat(repository.countByProjectId(projectId2)).isEqualTo(1);
        assertThat(repository.countByProjectId(UUID.randomUUID())).isEqualTo(0);
    }

    @Test
    void countByProjectIdThrowsExceptionWhenProjectIdIsNull() {
        assertThatThrownBy(() -> repository.countByProjectId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Project ID must not be null");
    }

    @Test
    void deleteExistingTaskReturnsTrueAndRemovesTask() {
        Task task = new Task(projectId1, "Test Task");
        repository.save(task);

        boolean deleted = repository.deleteById(task.getId());

        assertThat(deleted).isTrue();
        assertThat(repository.findById(task.getId())).isEmpty();
    }

    @Test
    void deleteNonExistentTaskReturnsFalse() {
        UUID nonExistentId = UUID.randomUUID();

        boolean deleted = repository.deleteById(nonExistentId);

        assertThat(deleted).isFalse();
    }
}