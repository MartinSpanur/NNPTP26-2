package cz.upce.fei.nnptp.taskpulse.storage;
/**
 * Defines a transaction boundary for accessing, committing, and rolling back a workspace.
 */
public interface IWorkspaceUnitOfWork extends AutoCloseable {

    ProjectRepository projects();

    TaskRepository tasks();

    boolean hasChanges();

    WorkspaceChangeSet changes();

    void commit();

    void rollback();

    @Override
    void close();
}