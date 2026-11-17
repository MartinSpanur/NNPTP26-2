package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.storage.WorkspaceChangeSet;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
/**
 * Compares workspace snapshots and identifies created, modified, and deleted entities.
 */
final class WorkspaceChangeDetector {

    WorkspaceChangeSet detect(
            WorkspaceSnapshot original,
            WorkspaceSnapshot current
    ) {
        Objects.requireNonNull(original, "Original snapshot must not be null");
        Objects.requireNonNull(current, "Current snapshot must not be null");

        Set<UUID> createdProjectIds = createdIds(
                original.projectRows(),
                current.projectRows()
        );
        Set<UUID> deletedProjectIds = deletedIds(
                original.projectRows(),
                current.projectRows()
        );
        Set<UUID> modifiedProjectIds = modifiedIds(
                original.projectRows(),
                current.projectRows()
        );

        Set<UUID> createdTaskIds = createdIds(
                original.taskRows(),
                current.taskRows()
        );
        Set<UUID> deletedTaskIds = deletedIds(
                original.taskRows(),
                current.taskRows()
        );
        Set<UUID> modifiedTaskIds = modifiedIds(
                original.taskRows(),
                current.taskRows()
        );

        for (UUID taskId : original.taskRows().keySet()) {
            if (!current.taskRows().containsKey(taskId)) {
                continue;
            }

            Object originalWorklogs = original.worklogRowsByTaskId().getOrDefault(taskId, java.util.List.of());
            Object currentWorklogs = current.worklogRowsByTaskId().getOrDefault(taskId, java.util.List.of());

            if (!Objects.equals(originalWorklogs, currentWorklogs)) {
                modifiedTaskIds.add(taskId);
            }
        }

        return new WorkspaceChangeSet(
                Set.copyOf(createdProjectIds),
                Set.copyOf(modifiedProjectIds),
                Set.copyOf(deletedProjectIds),
                Set.copyOf(createdTaskIds),
                Set.copyOf(modifiedTaskIds),
                Set.copyOf(deletedTaskIds)
        );
    }

    private static <T> Set<UUID> createdIds(
            Map<UUID, T> original,
            Map<UUID, T> current
    ) {
        Set<UUID> ids = new LinkedHashSet<>(current.keySet());
        ids.removeAll(original.keySet());
        return ids;
    }

    private static <T> Set<UUID> deletedIds(
            Map<UUID, T> original,
            Map<UUID, T> current
    ) {
        Set<UUID> ids = new LinkedHashSet<>(original.keySet());
        ids.removeAll(current.keySet());
        return ids;
    }

    private static <T> Set<UUID> modifiedIds(
            Map<UUID, T> original,
            Map<UUID, T> current
    ) {
        Set<UUID> ids = new LinkedHashSet<>();

        for (Map.Entry<UUID, T> entry : original.entrySet()) {
            UUID id = entry.getKey();
            if (current.containsKey(id)
                    && !Objects.equals(entry.getValue(), current.get(id))) {
                ids.add(id);
            }
        }

        return ids;
    }
}
