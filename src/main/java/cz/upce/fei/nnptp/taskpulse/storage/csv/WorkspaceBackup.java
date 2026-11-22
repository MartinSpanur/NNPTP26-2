package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Transfers complete workspaces using the existing UTF-8 CSV format. */
public class WorkspaceBackup {
    private static final List<String> FILES = List.of("projects.csv", "tasks.csv", "worklogs.csv");
    private static final List<String> HEADERS = List.of("id,name,description",
            "id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels",
            "taskId,timestamp,durationMinutes,author,description");
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private final ProjectRepository projects;
    private final TaskRepository tasks;

    public WorkspaceBackup(ProjectRepository projects, TaskRepository tasks) {
        this.projects = java.util.Objects.requireNonNull(projects);
        this.tasks = java.util.Objects.requireNonNull(tasks);
    }

    public void exportArchive(Path target) {
        Path staging = null;
        try {
            staging = Files.createTempDirectory("taskpulse-backup-");
            new CsvStorageManager(staging).save(projects, tasks);
            // CREATE_NEW protects existing backups from accidental overwrites.
            try (var zip = new ZipOutputStream(Files.newOutputStream(target,
                    java.nio.file.StandardOpenOption.CREATE_NEW))) {
                for (String name : FILES) {
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(staging.resolve(name), zip);
                    zip.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new StorageException("Failed to export workspace: " + e.getMessage(), e);
        } finally {
            cleanup(staging);
        }
    }

    /** Validates the entire bundle before replacing the in-memory workspace. */
    public void importBundle(Path source) {
        Path staging = null;
        try {
            staging = Files.createTempDirectory("taskpulse-import-");
            if (Files.isDirectory(source)) {
                for (String name : FILES) {
                    try (var input = Files.newInputStream(source.resolve(name))) {
                        copyBounded(input, staging.resolve(name));
                    }
                }
            } else {
                var seen = new HashSet<String>();
                try (var zip = new ZipInputStream(Files.newInputStream(source))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (!FILES.contains(name) || !seen.add(name)) {
                            throw new StorageException("Unexpected or duplicate ZIP entry: " + name);
                        }
                        copyBounded(zip, staging.resolve(name));
                    }
                }
            }
            for (int i = 0; i < FILES.size(); i++) {
                try (var reader = Files.newBufferedReader(staging.resolve(FILES.get(i)))) {
                    if (!HEADERS.get(i).equals(reader.readLine())) {
                        throw new StorageException("Invalid CSV header: " + FILES.get(i));
                    }
                }
            }
            var loaded = new CsvStorageManager(staging).load();
            tasks.findAll().forEach(task -> tasks.deleteById(task.getId()));
            projects.findAll().forEach(project -> projects.deleteById(project.getId()));
            loaded.projectRepository().findAll().forEach(projects::save);
            loaded.taskRepository().findAll().forEach(tasks::save);
        } catch (IOException e) {
            throw new StorageException("Failed to import workspace: " + e.getMessage(), e);
        } finally {
            cleanup(staging);
        }
    }

    private static void copyBounded(java.io.InputStream input, Path target) throws IOException {
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) {
                    throw new StorageException("CSV file exceeds 64 MiB limit");
                }
                output.write(buffer, 0, count);
            }
        }
    }

    private static void cleanup(Path directory) {
        if (directory == null) return;
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            directory.toFile().deleteOnExit();
        }
    }
}
