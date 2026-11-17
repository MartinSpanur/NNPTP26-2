package cz.upce.fei.nnptp.taskpulse.storage;

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