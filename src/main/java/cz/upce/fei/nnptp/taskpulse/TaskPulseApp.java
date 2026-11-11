package cz.upce.fei.nnptp.taskpulse;

import cz.upce.fei.nnptp.taskpulse.cli.TaskPulseCli;
import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.ProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.TaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.csv.CsvStorageManager;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Scanner;

public class TaskPulseApp {
    public static void main(String[] args) {
        Path dataDir = Paths.get(".taskpulse");
        CsvStorageManager storageManager = new CsvStorageManager(dataDir);
        CsvStorageManager.LoadedWorkspace workspace = storageManager.load();

        ProjectRepository projectRepository = workspace.projectRepository();
        TaskRepository taskRepository = workspace.taskRepository();

        ProjectService projectService = new ProjectService(projectRepository, taskRepository);
        TaskService taskService = new TaskService(taskRepository, projectRepository);

        if (args.length > 0 && "--demo".equalsIgnoreCase(args[0])) {
            runDemo(projectService, taskService, System.out);
            return;
        }

        TaskPulseCli cli = new TaskPulseCli(projectService, storageManager, taskService);
        if (args.length > 0) {
            String commandLine = String.join(" ", args);
            cli.executeLine(commandLine);
            storageManager.save(projectRepository, taskRepository);
        } else {
            Scanner scanner = new Scanner(System.in);
            cli.runInteractive(scanner);
            storageManager.save(projectRepository, taskRepository);
        }
    }

    public static void runDemo(ProjectService projectService, TaskService taskService, PrintStream out) {
        out.println("=== TaskPulse End-to-End Demo ===");

        Project project = projectService.createProject("TaskPulse Development", "Coursework task tracker");
        out.println("Created Project: " + project.getName() + " (" + project.getId() + ")");

        Task task1 = taskService.createTask(
                project.getId(),
                "Implement domain model",
                "Create Project, Task, Worklog, Priority, State",
                Priority.HIGH,
                120,
                LocalDate.now().plusDays(2)
        );
        taskService.addLabel(task1.getId(), "domain");
        taskService.addLabel(task1.getId(), "core");
        out.println("Created Task: " + task1.getTitle() + " [" + task1.getPriority() + "] (Labels: " + task1.getLabels() + ")");

        out.println("Transitioning task to IN_PROGRESS...");
        taskService.transitionTask(task1.getId(), TaskState.IN_PROGRESS);

        out.println("Logging work...");
        taskService.logWork(task1.getId(), 45, "Student Developer", "Domain classes and invariants");
        taskService.logWork(task1.getId(), 60, "Student Developer", "Unit tests for lifecycle");

        out.println("Recorded minutes: " + taskService.getTask(task1.getId()).getTotalRecordedMinutes());
        out.println("Transitioning task to DONE...");
        taskService.transitionTask(task1.getId(), TaskState.DONE);

        Task finishedTask = taskService.getTask(task1.getId());
        out.println("Finished Task: " + finishedTask.getTitle() + " state: " + finishedTask.getState() +
                ", worklogs: " + finishedTask.getWorklogs().size() + ", variance: " + finishedTask.getVarianceMinutes() + " min");

        out.println("=== Demo Finished Successfully ===");
    }
}
