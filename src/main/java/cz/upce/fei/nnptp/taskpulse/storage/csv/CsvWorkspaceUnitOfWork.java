package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.storage.IWorkspaceUnitOfWork;
import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.WorkspaceChangeSet;

import java.util.Objects;
/**
 * Coordinates change tracking, atomic persistence, and rollback for a CSV workspace.
 */
final class CsvWorkspaceUnitOfWork implements IWorkspaceUnitOfWork {
    private final CsvStorageManager storageManager;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceSnapshotFactory snapshotFactory;
    private final WorkspaceChangeDetector changeDetector;
    private final WorkspaceSnapshot originalSnapshot;

    private WorkspaceUnitOfWorkState state;

    CsvWorkspaceUnitOfWork(
            CsvStorageManager storageManager,
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            WorkspaceSnapshotFactory snapshotFactory,
            WorkspaceChangeDetector changeDetector,
            WorkspaceSnapshot originalSnapshot
    ) {
        this.storageManager = Objects.requireNonNull(storageManager, "Storage manager must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "Project repository must not be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "Task repository must not be null");
        this.snapshotFactory = Objects.requireNonNull(snapshotFactory, "Snapshot factory must not be null");
        this.changeDetector = Objects.requireNonNull(changeDetector, "Change detector must not be null");
        this.originalSnapshot = Objects.requireNonNull(originalSnapshot, "Original snapshot must not be null");
        this.state = WorkspaceUnitOfWorkState.ACTIVE;
    }

    @Override
    public ProjectRepository projects() {
        requireActive();
        return projectRepository;
    }

    @Override
    public TaskRepository tasks() {
        requireActive();
        return taskRepository;
    }

    @Override
    public boolean hasChanges() {
        return !changes().isEmpty();
    }

    @Override
    public WorkspaceChangeSet changes() {
        requireActive();
        WorkspaceSnapshot currentSnapshot = snapshotFactory.create(projectRepository, taskRepository);
        return changeDetector.detect(originalSnapshot, currentSnapshot);
    }

    @Override
    public void commit() {
        requireActive();

        try {
            storageManager.commitAtomically(
                    projectRepository,
                    taskRepository
            );
            state = WorkspaceUnitOfWorkState.COMMITTED;
        } catch (RuntimeException exception) {
            state = WorkspaceUnitOfWorkState.FAILED;
            throw exception;
        }
    }

    @Override
    public void rollback() {
        requireActive();

        try {
            storageManager.restoreRepositories(
                    originalSnapshot,
                    projectRepository,
                    taskRepository
            );
            state = WorkspaceUnitOfWorkState.ROLLED_BACK;
        } catch (RuntimeException exception) {
            state = WorkspaceUnitOfWorkState.FAILED;
            throw exception;
        }
    }

    @Override
    public void close() {
        if (state == WorkspaceUnitOfWorkState.ACTIVE) {
            rollback();
        }
    }

    private void requireActive() {
        if (state != WorkspaceUnitOfWorkState.ACTIVE) {
            throw new IllegalStateException(
                    "Workspace unit of work is not active; current state: " + state
            );
        }
    }
}
