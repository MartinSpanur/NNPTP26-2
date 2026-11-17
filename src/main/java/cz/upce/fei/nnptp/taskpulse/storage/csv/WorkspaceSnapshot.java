package cz.upce.fei.nnptp.taskpulse.storage.csv;

import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * Captures an immutable CSV representation of all persisted workspace entities.
 */
record WorkspaceSnapshot(
        Map<UUID, List<String>> projectRows,
        Map<UUID, List<String>> taskRows,
        Map<UUID, List<List<String>>> worklogRowsByTaskId
) {
}