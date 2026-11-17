package cz.upce.fei.nnptp.taskpulse.storage;

import java.util.Set;
import java.util.UUID;
/**
 * Contains the identifiers of projects and tasks created, modified, or deleted in a workspace.
 */
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