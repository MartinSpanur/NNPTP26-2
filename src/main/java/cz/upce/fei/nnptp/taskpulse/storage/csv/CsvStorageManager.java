package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.*;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControl.NotImplementedException;

public class CsvStorageManager {
    private final Path dataDirectory;

    public CsvStorageManager(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "Data directory must not be null");
    }

    public record LoadedWorkspace(ProjectRepository projectRepository, TaskRepository taskRepository) {}

    @FunctionalInterface
    private interface FieldParser<T> {
        T parse(String raw);
    }

    private <T> T parseField(String file, String columnName, int lineNumber, String raw, FieldParser<T> parser) {
        try {
            return parser.parse(raw.strip());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new StorageException("Malformed value in " + file + " at line " + lineNumber
                    + ", column '" + columnName + "': invalid token '" + raw + "'", e);
        }
    }

    public LoadedWorkspace load() {
        InMemoryProjectRepository projectRepo = new InMemoryProjectRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        if (!Files.exists(dataDirectory)) {
            return new LoadedWorkspace(projectRepo, taskRepo);
        }

        Path projectsPath = dataDirectory.resolve("projects.csv");
        Path tasksPath = dataDirectory.resolve("tasks.csv");
        Path worklogsPath = dataDirectory.resolve("worklogs.csv");

        Set<UUID> projectIds = new HashSet<>();
        if (Files.exists(projectsPath)) {
            loadProjects(projectsPath, projectRepo, projectIds);
        }

        Map<UUID, Task> taskMap = new HashMap<>();
        if (Files.exists(tasksPath)) {
            loadTasks(tasksPath, taskRepo, projectIds, taskMap);
        }

        if (Files.exists(worklogsPath)) {
            loadWorklogs(worklogsPath, taskMap);
        }

        return new LoadedWorkspace(projectRepo, taskRepo);
    }

    private void loadProjects(Path path, InMemoryProjectRepository projectRepo, Set<UUID> projectIds) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<List<String>> rows = CsvUtils.parse(reader);
            if (rows.isEmpty()) return;
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.isEmpty() || (row.size() == 1 && row.get(0).isBlank())) continue;
                if (row.size() < 3) {
                    throw new StorageException("Malformed row in projects.csv at line " + (i + 1));
                }
                UUID id = parseField("projects.csv", "id", i + 1, row.get(0), UUID::fromString);
                if (!projectIds.add(id)) {
                    throw new StorageException("Duplicate project id detected: " + id);
                }
                String name = row.get(1);
                String description = row.get(2);
                projectRepo.save(new Project(id, name, description));
            }
        } catch (Exception e) {
            throw new StorageException("Failed to load projects from CSV: " + e.getMessage(), e);
        }
    }

    private void loadTasks(Path path, InMemoryTaskRepository taskRepo, Set<UUID> projectIds, Map<UUID, Task> taskMap) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<List<String>> rows = CsvUtils.parse(reader);
            if (rows.isEmpty()) return;
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.isEmpty() || (row.size() == 1 && row.get(0).isBlank())) continue;
                if (row.size() < 9) {
                    throw new StorageException("Malformed row in tasks.csv at line " + (i + 1));
                }
                UUID id = parseField("tasks.csv", "id", i + 1, row.get(0), UUID::fromString);
                UUID projectId = parseField("tasks.csv", "projectId", i + 1, row.get(1), UUID::fromString);
                if (!projectIds.contains(projectId)) {
                    throw new StorageException("Task references missing project: " + projectId);
                }
                if (taskMap.containsKey(id)) {
                    throw new StorageException("Duplicate task id detected: " + id);
                }
                String title = row.get(2);
                String description = row.get(3);
                Priority priority = parseField("tasks.csv", "priority", i + 1, row.get(4), Priority::valueOf);
                TaskState state = parseField("tasks.csv", "state", i + 1, row.get(5), TaskState::valueOf);
                Integer estimatedMinutes = row.get(6).isBlank()
                        ? null
                        : parseField("tasks.csv", "estimatedMinutes", i + 1, row.get(6), Integer::valueOf);
                LocalDate dueDate = row.get(7).isBlank()
                        ? null
                        : parseField("tasks.csv", "dueDate", i + 1, row.get(7), LocalDate::parse);
                String rawLabels = row.get(8);
                List<String> labels = new ArrayList<>();
                if (!rawLabels.isEmpty()) {
                    labels = Arrays.asList(rawLabels.split(";", -1));
                }

                Task task = new TaskBuilder(projectId, title)
                        .id(id)
                        .description(description)
                        .priority(priority)
                        .state(state)
                        .estimatedMinutes(estimatedMinutes)
                        .dueDate(dueDate)
                        .labels(labels)
                        .build();

                taskRepo.save(task);
                taskMap.put(id, task);
            }
        } catch (Exception e) {
            throw new StorageException("Failed to load tasks from CSV: " + e.getMessage(), e);
        }
    }

    private void loadWorklogs(Path path, Map<UUID, Task> taskMap) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<List<String>> rows = CsvUtils.parse(reader);
            if (rows.isEmpty()) return;
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.isEmpty() || (row.size() == 1 && row.get(0).isBlank())) continue;
                if (row.size() < 5) {
                    throw new StorageException("Malformed row in worklogs.csv at line " + (i + 1));
                }
                UUID taskId = parseField("worklogs.csv", "taskId", i + 1, row.get(0), UUID::fromString);
                Task task = taskMap.get(taskId);
                if (task == null) {
                    throw new StorageException("Worklog references missing task: " + taskId);
                }
                LocalDateTime timestamp = parseField("worklogs.csv", "timestamp", i + 1, row.get(1), LocalDateTime::parse);
                long durationMinutes = parseField("worklogs.csv", "durationMinutes", i + 1, row.get(2), Long::parseLong);
                String author = row.get(3);
                String description = row.get(4);
                Worklog worklog = new Worklog(timestamp, durationMinutes, author, description);
                task.restoreWorklog(worklog);
            }
        } catch (Exception e) {
            throw new StorageException("Failed to load worklogs from CSV: " + e.getMessage(), e);
        }
    }

    public void save(ProjectRepository projectRepository, TaskRepository taskRepository) {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            saveProjects(dataDirectory.resolve("projects.csv"), projectRepository.findAll());
            List<Task> tasks = taskRepository.findAll();
            saveTasks(dataDirectory.resolve("tasks.csv"), tasks);
            saveWorklogs(dataDirectory.resolve("worklogs.csv"), tasks);
        } catch (IOException e) {
            throw new StorageException("Failed to save workspace to CSV: " + e.getMessage(), e);
        }
    }
    
    public void exportArchive(Path archivePath){
        throw new UnsupportedOperationException(
            "Workspace archive export is not implemented yet"
        );
    }

    public LoadedWorkspace importArchive(Path archivePath){
        throw new UnsupportedOperationException(
            "Workspace archive import is not implemented yet"
        );
    }

    private void saveProjects(Path path, List<Project> projects) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("id,name,description\n");
            for (Project p : projects) {
                writer.write(p.getId().toString() + "," +
                        CsvUtils.escape(p.getName()) + "," +
                        CsvUtils.escape(p.getDescription()) + "\n");
            }
        }
    }

    private void saveTasks(Path path, List<Task> tasks) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("id,projectId,title,description,priority,state,estimatedMinutes,dueDate,labels\n");
            for (Task t : tasks) {
                String labels = String.join(";", t.getLabels());
                writer.write(t.getId().toString() + "," +
                        t.getProjectId().toString() + "," +
                        CsvUtils.escape(t.getTitle()) + "," +
                        CsvUtils.escape(t.getDescription()) + "," +
                        t.getPriority().name() + "," +
                        t.getState().name() + "," +
                        (t.getEstimatedMinutes() == null ? "" : t.getEstimatedMinutes().toString()) + "," +
                        (t.getDueDate() == null ? "" : t.getDueDate().toString()) + "," +
                        CsvUtils.escape(labels) + "\n");
            }
        }
    }

    private void saveWorklogs(Path path, List<Task> tasks) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("taskId,timestamp,durationMinutes,author,description\n");
            for (Task t : tasks) {
                for (Worklog w : t.getWorklogs()) {
                    writer.write(t.getId().toString() + "," +
                            w.timestamp().toString() + "," +
                            w.durationMinutes() + "," +
                            CsvUtils.escape(w.author()) + "," +
                            CsvUtils.escape(w.description()) + "\n");
                }
            }
        }
    }
}
