package cz.upce.fei.nnptp.taskpulse.service;

import cz.upce.fei.nnptp.taskpulse.domain.*;
import cz.upce.fei.nnptp.taskpulse.domain.exception.ResourceNotFoundException;
import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository, Clock clock) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "TaskRepository must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "ProjectRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this(taskRepository, projectRepository, Clock.systemDefaultZone());
    }

    public Task createTask(UUID projectId, String title, String description, Priority priority,
                           Integer estimatedMinutes, LocalDate dueDate) {
        Objects.requireNonNull(projectId, "Project ID must not be null");
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }
        Task task = new TaskBuilder(projectId, title)
                .description(description)
                .priority(priority)
                .estimatedMinutes(estimatedMinutes)
                .dueDate(dueDate)
                .build();
        return taskRepository.save(task);
    }

    public Task getTask(UUID id) {
        Objects.requireNonNull(id, "Task ID must not be null");
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
    }

    public List<Task> listTasks() {
        return taskRepository.findAll();
    }

    public List<Task> listTasksForProject(UUID projectId) {
        Objects.requireNonNull(projectId, "Project ID must not be null");
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }
        return taskRepository.findByProjectId(projectId);
    }

    public Task updateTask(UUID id, String title, String description, Priority priority,
                           Integer estimatedMinutes, LocalDate dueDate) {
        Task task = getTask(id);
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);
        task.setEstimatedMinutes(estimatedMinutes);
        task.setDueDate(dueDate);
        return taskRepository.save(task);
    }

    public void deleteTask(UUID id) {
        getTask(id);
        taskRepository.deleteById(id);
    }

    public Task addLabel(UUID taskId, String label) {
        Task task = getTask(taskId);
        task.addLabel(label);
        return taskRepository.save(task);
    }

    public Task removeLabel(UUID taskId, String label) {
        Task task = getTask(taskId);
        task.removeLabel(label);
        return taskRepository.save(task);
    }

    public Task transitionTask(UUID taskId, TaskState nextState) {
        Task task = getTask(taskId);
        task.transitionTo(nextState);
        return taskRepository.save(task);
    }

    public Worklog logWork(UUID taskId, long durationMinutes, String author, String description) {
        Task task = getTask(taskId);
        LocalDateTime now = LocalDateTime.now(clock);
        Worklog worklog = new Worklog(now, durationMinutes, author, description);
        task.addWorklog(worklog);
        taskRepository.save(task);
        return worklog;
    }

    public List<Task> findTasks(TaskQuery query) {
        Objects.requireNonNull(query, "Query must not be null");
        LocalDate today = LocalDate.now(clock);
        TaskSpecification specification = TaskQueryEvaluator.toSpecification(query, today);

        List<Task> result = new ArrayList<>(taskRepository.findAll().stream()
                .filter(specification::isSatisfiedBy)
                .toList());

        result.sort(buildComparator(query.sortOrder()));
        return result;
    }

    private Comparator<Task> buildComparator(TaskSortOrder sortOrder) {
        Comparator<Task> titleAsc = Comparator.comparing(
                (Task t) -> t.getTitle().toLowerCase(Locale.ROOT)
        );
        Comparator<Task> idTieBreaker = Comparator.comparing(
                (Task t) -> t.getId().toString()
        );

        Comparator<Task> priorityDesc = Comparator.comparingInt(
                (Task t) -> t.getPriority().getLevel()
        ).reversed();

        Comparator<Task> dueDateAsc = Comparator.comparing(
                Task::getDueDate,
                Comparator.nullsLast(Comparator.naturalOrder())
        );

        return switch (sortOrder) {
            case TITLE_ASC -> titleAsc.thenComparing(idTieBreaker);
            case PRIORITY_DESC -> priorityDesc.thenComparing(titleAsc).thenComparing(idTieBreaker);
            case DUE_DATE_ASC -> dueDateAsc.thenComparing(titleAsc).thenComparing(idTieBreaker);
            case PRIORITY_DESC_THEN_DUE_DATE_ASC -> priorityDesc.thenComparing(dueDateAsc).thenComparing(titleAsc).thenComparing(idTieBreaker);
        };
    }
}
