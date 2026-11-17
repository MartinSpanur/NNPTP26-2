package cz.upce.fei.nnptp.taskpulse.storage.csv;
/**
 * Represents the lifecycle state of a workspace unit of work.
 */
enum WorkspaceUnitOfWorkState {
    ACTIVE,
    COMMITTED,
    ROLLED_BACK,
    FAILED
}