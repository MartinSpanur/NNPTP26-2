package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ProjectCliHandler implements CliCommandHandler {
    private final ProjectService projectService;
    private final PrintStream out;
    private final PrintStream err;

    ProjectCliHandler(ProjectService projectService, PrintStream out, PrintStream err) {
        this.projectService = projectService;
        this.out = out;
        this.err = err;
    }

    @Override
    public void handle(List<String> args) {
        if (args.isEmpty()) {
            err.println("Usage: project <add|list|show|edit|delete> [arguments]");
            return;
        }

        String action = args.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> {
                if (args.size() < 2) {
                    err.println("Usage: project add <name> [description]");
                    return;
                }
                String name = args.get(1);
                String desc = args.size() > 2 ? args.get(2) : "";
                Project project = projectService.createProject(name, desc);
                out.println("Project created: " + project.getId() + " - " + project.getName());
            }
            case "list" -> {
                List<Project> projects = projectService.listProjects();
                if (projects.isEmpty()) {
                    out.println("No projects found.");
                } else {
                    for (Project p : projects) {
                        out.println(p.getId() + " | " + p.getName() + (p.getDescription().isEmpty() ? "" : " | " + p.getDescription()));
                    }
                }
            }
            case "show" -> {
                if (args.size() < 2) {
                    err.println("Usage: project show <project-id>");
                    return;
                }
                UUID id = TaskPulseCli.parseUuid(args.get(1));
                Project p = projectService.getProject(id);
                out.println("ID:          " + p.getId());
                out.println("Name:        " + p.getName());
                out.println("Description: " + p.getDescription());
            }
            case "edit" -> {
                if (args.size() < 3) {
                    err.println("Usage: project edit <project-id> <name> [description]");
                    return;
                }
                UUID id = TaskPulseCli.parseUuid(args.get(1));
                String name = args.get(2);
                String desc = args.size() > 3 ? args.get(3) : projectService.getProject(id).getDescription();
                Project p = projectService.updateProject(id, name, desc);
                out.println("Project updated: " + p.getId() + " - " + p.getName());
            }
            case "delete" -> {
                if (args.size() < 2) {
                    err.println("Usage: project delete <project-id>");
                    return;
                }
                UUID id = TaskPulseCli.parseUuid(args.get(1));
                projectService.deleteProject(id);
                out.println("Project deleted: " + id);
            }
            
            default -> err.println("Unknown project action: '" + action + "'");
        }
    }
}
