package cz.upce.fei.nnptp.taskpulse.storage;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryProjectRepositoryTest {

    private InMemoryProjectRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProjectRepository();
    }

    @Test
    void saveNewProject_StoresAndReturnsProject() {
        Project project = new Project("Mobile App", "Android and iOS client");

        // verify save returns the same instance and can be found
        Project saved = repository.save(project);

        assertThat(saved).isSameAs(project);
        assertThat(repository.existsById(project.getId())).isTrue();
        assertThat(repository.findById(project.getId())).contains(project);
    }

    @Test
    void saveExistingProject_UpdatesStoredProject() {
        UUID id = UUID.randomUUID();
        Project initial = new Project(id, "Old Name", "Old Description");
        repository.save(initial);

        // check updating project with same id
        Project updated = new Project(id, "New Name", "New Description");
        repository.save(updated);

        Optional<Project> found = repository.findById(id);
        assertThat(found).contains(updated);
        assertThat(found.get().getName()).isEqualTo("New Name");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void save_ThrowsNullPointerExceptionWhenProjectIsNull() {
        // verify null project is rejected
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Project must not be null");
    }

    @Test
    void findById_ReturnsEmptyWhenNotFound() {
        UUID nonExistentId = UUID.randomUUID();

        assertThat(repository.findById(nonExistentId)).isEmpty();
    }

    @Test
    void findById_ReturnsEmptyWhenIdIsNull() {
        assertThat(repository.findById(null)).isEmpty();
    }

    @Test
    void findAll_ReturnsEmptyListWhenNoProjectsStored() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void findAll_ReturnsAllProjectsPreservingInsertionOrder() {
        Project p1 = new Project("Alpha Project");
        Project p2 = new Project("Beta Project");
        Project p3 = new Project("Gamma Project");

        repository.save(p1);
        repository.save(p2);
        repository.save(p3);

        // should maintain order of insertion
        assertThat(repository.findAll()).containsExactly(p1, p2, p3);
    }

    @Test
    void findAll_ReturnsUnmodifiableList() {
        repository.save(new Project("Alpha"));
        List<Project> allProjects = repository.findAll();

        // ensure returned list can not be modified directly
        assertThatThrownBy(() -> allProjects.add(new Project("Beta")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deleteById_RemovesExistingProjectAndReturnsTrue() {
        Project project = new Project("To Delete");
        repository.save(project);

        boolean deleted = repository.deleteById(project.getId());

        assertThat(deleted).isTrue();
        assertThat(repository.existsById(project.getId())).isFalse();
        assertThat(repository.findById(project.getId())).isEmpty();
    }

    @Test
    void deleteById_ReturnsFalseWhenProjectDoesNotExist() {
        boolean deleted = repository.deleteById(UUID.randomUUID());

        assertThat(deleted).isFalse();
    }

    @Test
    void deleteById_ReturnsFalseWhenIdIsNull() {
        boolean deleted = repository.deleteById(null);

        assertThat(deleted).isFalse();
    }

    @Test
    void existsById_ReturnsTrueForExistingProject() {
        Project project = new Project("Existing");
        repository.save(project);

        assertThat(repository.existsById(project.getId())).isTrue();
    }

    @Test
    void existsById_ReturnsFalseForNonExistingProject() {
        assertThat(repository.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void existsById_ReturnsFalseWhenIdIsNull() {
        assertThat(repository.existsById(null)).isFalse();
    }
}
