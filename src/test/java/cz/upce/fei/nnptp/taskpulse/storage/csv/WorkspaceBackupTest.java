package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.*;
import cz.upce.fei.nnptp.taskpulse.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;

class WorkspaceBackupTest {
    @TempDir Path directory;
    private final ProjectRepository projects = new InMemoryProjectRepository();
    private final TaskRepository tasks = new InMemoryTaskRepository();
    private final WorkspaceBackup backup = new WorkspaceBackup(projects, tasks);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void restoresCompleteWorkspaceFromArchiveOrDirectory(boolean archive) throws Exception {
        Project project = projects.save(new Project("Žluťoučký, projekt", "line\n\"quoted\""));
        Task task = new TaskBuilder(project.getId(), "Task, title")
                .description("Description\nnext").priority(Priority.HIGH)
                .state(TaskState.DONE).estimatedMinutes(60).dueDate(LocalDate.of(2026, 11, 12))
                .labels(List.of("one", "two")).build();
        task.restoreWorklog(new Worklog(LocalDateTime.of(2026, 11, 11, 12, 30), 45, "Author", "Notes"));
        tasks.save(task);
        Path source = directory.resolve(archive ? "backup.zip" : "csv");
        if (archive) backup.exportArchive(source);
        else new CsvStorageManager(source).save(projects, tasks);
        tasks.deleteById(task.getId());
        projects.deleteById(project.getId());
        Project replaced = projects.save(new Project("Remove me", ""));

        backup.importBundle(source);

        assertThat(projects.existsById(replaced.getId())).isFalse();
        assertThat(projects.findById(project.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(project);
        assertThat(tasks.findById(task.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(task);
    }

    @Test
    void roundTripsEmptyWorkspaceAndDoesNotOverwriteBackup() {
        Path zip = directory.resolve("empty.zip");
        backup.exportArchive(zip);
        assertThatThrownBy(() -> backup.exportArchive(zip)).isInstanceOf(StorageException.class);
        backup.importBundle(zip);
        assertThat(projects.findAll()).isEmpty();
        assertThat(tasks.findAll()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "header", "relationship", "malformed"})
    void invalidBundleLeavesExistingWorkspaceIntact(String defect) throws Exception {
        Project original = projects.save(new Project("Keep me", ""));
        Path source = directory.resolve("invalid");
        new CsvStorageManager(source).save(new InMemoryProjectRepository(), new InMemoryTaskRepository());
        switch (defect) {
            case "missing" -> Files.delete(source.resolve("worklogs.csv"));
            case "header" -> Files.writeString(source.resolve("projects.csv"), "wrong,header\n");
            case "relationship" -> Files.writeString(source.resolve("worklogs.csv"),
                    "taskId,timestamp,durationMinutes,author,description\n"
                            + java.util.UUID.randomUUID() + ",2026-11-11T12:30,10,Author,notes\n");
            case "malformed" -> Files.writeString(source.resolve("projects.csv"), "id,name,description\ninvalid,name,description\n");
        }
        assertThatThrownBy(() -> backup.importBundle(source)).isInstanceOf(StorageException.class);
        assertThat(projects.findAll()).containsExactly(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escaped.csv", "/absolute.csv", "unknown.csv", "projects.csv"})
    void rejectsUnsafeUnexpectedOrIncompleteArchives(String entry) throws Exception {
        Path zip = directory.resolve("bad.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry(entry));
            output.write("id,name,description\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        assertThatThrownBy(() -> backup.importBundle(zip)).isInstanceOf(StorageException.class);
        assertThat(Files.exists(directory.resolve("escaped.csv"))).isFalse();
    }

    @Test
    void rejectsOversizedCompressedCsvBeforeChangingWorkspace() throws Exception {
        Project original = projects.save(new Project("Keep me", ""));
        Path zip = directory.resolve("large.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("projects.csv"));
            byte[] block = new byte[1024 * 1024];
            for (int i = 0; i < 65; i++) output.write(block);
            output.closeEntry();
        }
        assertThatThrownBy(() -> backup.importBundle(zip)).isInstanceOf(StorageException.class)
                .hasMessageContaining("64 MiB");
        assertThat(projects.findAll()).containsExactly(original);
    }

}
