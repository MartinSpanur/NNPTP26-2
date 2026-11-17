package cz.upce.fei.nnptp.taskpulse.storage.csv;

import java.io.IOException;
import java.nio.file.Path;

class WorkspaceFileOperations {

    void moveAtomically(Path source, Path target)
            throws IOException {
        // Files.move(..., ATOMIC_MOVE)
    }

    void deleteDirectoryIfExists(Path directory)
            throws IOException {
        // Recursive deletion
    }
}