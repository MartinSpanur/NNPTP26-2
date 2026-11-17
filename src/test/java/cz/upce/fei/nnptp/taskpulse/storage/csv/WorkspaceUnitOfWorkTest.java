package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.storage.IWorkspaceUnitOfWork;
import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.WorkspaceChangeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceUnitOfWorkTest {

    @TempDir
    Path workspace;

    @Test
    void tracksCreatedModifiedAndDeletedEntities() {
        CsvStorageManager storage = new CsvStorageManager(workspace);
        Project project;
        Task task;

        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            project = unitOfWork.projects().save(new Project("Original"));
            task = unitOfWork.tasks().save(new Task(project.getId(), "Task"));

            WorkspaceChangeSet changes = unitOfWork.changes();
            assertThat(changes.createdProjectIds()).containsExactly(project.getId());
            assertThat(changes.createdTaskIds()).containsExactly(task.getId());

            unitOfWork.commit();
        }

        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            Project loadedProject = unitOfWork.projects().findById(project.getId()).orElseThrow();
            Task loadedTask = unitOfWork.tasks().findById(task.getId()).orElseThrow();
            loadedProject.setName("Changed");
            loadedTask.addLabel("changed");

            WorkspaceChangeSet changes = unitOfWork.changes();
            assertThat(changes.modifiedProjectIds()).containsExactly(project.getId());
            assertThat(changes.modifiedTaskIds()).containsExactly(task.getId());
        }

        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            unitOfWork.tasks().deleteById(task.getId());
            unitOfWork.projects().deleteById(project.getId());

            WorkspaceChangeSet changes = unitOfWork.changes();
            assertThat(changes.deletedProjectIds()).containsExactly(project.getId());
            assertThat(changes.deletedTaskIds()).containsExactly(task.getId());
        }
    }

    @Test
    void rollbackRestoresOriginalInMemoryState() {
        CsvStorageManager storage = new CsvStorageManager(workspace);
        Project project;

        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            project = unitOfWork.projects().save(new Project("Original"));
            unitOfWork.commit();
        }

        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            ProjectRepository projects = unitOfWork.projects();
            TaskRepository tasks = unitOfWork.tasks();
            projects.findById(project.getId()).orElseThrow().setName("Changed");
            tasks.save(new Task(project.getId(), "Temporary"));

            unitOfWork.rollback();

            assertThat(projects.findById(project.getId()).orElseThrow().getName()).isEqualTo("Original");
            assertThat(tasks.findAll()).isEmpty();
        }
    }

    @Test
    void failedAtomicReplacementKeepsOriginalWorkspace() {
        CsvStorageManager storage = new CsvStorageManager(workspace);
        Project project;

        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            project = unitOfWork.projects().save(new Project("Original"));
            unitOfWork.commit();
        }

        CsvStorageManager failingStorage = new CsvStorageManager(
                workspace,
                new FailSecondMoveFileOperations()
        );

        try (IWorkspaceUnitOfWork unitOfWork = failingStorage.openUnitOfWork()) {
            unitOfWork.projects().findById(project.getId()).orElseThrow().setName("Changed");
            assertThatThrownBy(unitOfWork::commit).isInstanceOf(StorageException.class);
        }

        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            Project reloaded = unitOfWork.projects().findById(project.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("Original");
        }
    }

    private static class FailSecondMoveFileOperations extends WorkspaceFileOperations {
        private int moveCount;

        @Override
        void moveAtomically(Path source, Path target) throws IOException {
            moveCount++;
            if (moveCount == 2) {
                throw new IOException("Simulated activation failure");
            }
            super.moveAtomically(source, target);
        }
    }
}
