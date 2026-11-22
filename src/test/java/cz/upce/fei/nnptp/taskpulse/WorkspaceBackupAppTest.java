package cz.upce.fei.nnptp.taskpulse;

import cz.upce.fei.nnptp.taskpulse.storage.csv.CsvStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceBackupAppTest {
    @TempDir Path directory;

    @Test
    void importedWorkspaceSurvivesApplicationRestart() {
        Path original = directory.resolve("original");
        Path restored = directory.resolve("restored");
        Path archive = directory.resolve("backup with spaces.zip");
        TaskPulseApp.run(new String[]{"project", "add", "Backed up project"}, original);
        TaskPulseApp.run(new String[]{"workspace", "export", archive.toString()}, original);
        TaskPulseApp.run(new String[]{"project", "add", "Replace this project"}, restored);
        TaskPulseApp.run(new String[]{"workspace", "import", archive.toString()}, restored);
        var expected = new CsvStorageManager(original).load();
        var actual = new CsvStorageManager(restored).load();
        assertThat(actual.projectRepository().findAll()).usingRecursiveComparison()
                .isEqualTo(expected.projectRepository().findAll());
        assertThat(actual.taskRepository().findAll()).isEmpty();
    }
}
