package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
/**
 * Creates workspace snapshots from the current project and task repositories.
 */
final class WorkspaceSnapshotFactory {
    private final ProjectCsvMapper projectMapper;
    private final TaskCsvMapper taskMapper;
    private final WorklogCsvMapper worklogMapper;

    WorkspaceSnapshotFactory(
            ProjectCsvMapper projectMapper,
            TaskCsvMapper taskMapper,
            WorklogCsvMapper worklogMapper
    ) {
        this.projectMapper = Objects.requireNonNull(projectMapper, "Project mapper must not be null");
        this.taskMapper = Objects.requireNonNull(taskMapper, "Task mapper must not be null");
        this.worklogMapper = Objects.requireNonNull(worklogMapper, "Worklog mapper must not be null");
    }

    WorkspaceSnapshot create(
            ProjectRepository projectRepository,
            TaskRepository taskRepository
    ) {
        Objects.requireNonNull(projectRepository, "Project repository must not be null");
        Objects.requireNonNull(taskRepository, "Task repository must not be null");

        Map<UUID, List<String>> projectRows = new LinkedHashMap<>();
        for (Project project : projectRepository.findAll()) {
            projectRows.put(
                    project.getId(),
                    List.copyOf(projectMapper.toFields(project))
            );
        }

        Map<UUID, List<String>> taskRows = new LinkedHashMap<>();
        Map<UUID, List<List<String>>> worklogRowsByTaskId = new LinkedHashMap<>();

        for (Task task : taskRepository.findAll()) {
            UUID taskId = task.getId();
            taskRows.put(
                    taskId,
                    List.copyOf(taskMapper.toFields(task))
            );

            List<List<String>> worklogRows = new ArrayList<>();
            task.getWorklogs().forEach(worklog -> {
                WorklogCsvMapper.TaskWorklog taskWorklog =
                        new WorklogCsvMapper.TaskWorklog(taskId, worklog);
                worklogRows.add(List.copyOf(worklogMapper.toFields(taskWorklog)));
            });

            worklogRowsByTaskId.put(
                    taskId,
                    Collections.unmodifiableList(worklogRows)
            );
        }

        return new WorkspaceSnapshot(
                Collections.unmodifiableMap(projectRows),
                Collections.unmodifiableMap(taskRows),
                Collections.unmodifiableMap(worklogRowsByTaskId)
        );
    }
}
