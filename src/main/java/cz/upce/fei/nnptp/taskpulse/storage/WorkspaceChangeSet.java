package cz.upce.fei.nnptp.taskpulse.storage;

import java.util.Set;
import java.util.UUID;

public record WorkspaceChangeSet(
        Set<UUID> createdProjectIds,
        Set<UUID> modifiedProjectIds,
        Set<UUID> deletedProjectIds,
        Set<UUID> createdTaskIds,
        Set<UUID> modifiedTaskIds,
        Set<UUID> deletedTaskIds
) {

    public boolean isEmpty() {
        return createdProjectIds.isEmpty()
                && modifiedProjectIds.isEmpty()
                && deletedProjectIds.isEmpty()
                && createdTaskIds.isEmpty()
                && modifiedTaskIds.isEmpty()
                && deletedTaskIds.isEmpty();
    }
}