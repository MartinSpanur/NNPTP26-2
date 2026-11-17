package cz.upce.fei.nnptp.taskpulse.storage.csv;

enum WorkspaceUnitOfWorkState {
    ACTIVE,
    COMMITTED,
    ROLLED_BACK,
    FAILED
}