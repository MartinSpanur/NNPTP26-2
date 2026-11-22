package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProjectCliHandlerTest {
    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private ProjectService service;
    private ProjectCliHandler handler;

    @BeforeEach
    void setUp() {
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        var projects = new InMemoryProjectRepository();
        var tasks = new InMemoryTaskRepository();
        service = new ProjectService(projects, tasks);
        handler = new ProjectCliHandler(service,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @Test
    void emptyCommandAndMissingArgumentsPrintUsage() {
        handler.handle(List.of());
        handler.handle(List.of("add"));
        handler.handle(List.of("show"));
        handler.handle(List.of("edit", UUID.randomUUID().toString()));
        handler.handle(List.of("delete"));
        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("Usage: project <add|list|show|edit|delete>",
                        "Usage: project add", "Usage: project show",
                        "Usage: project edit", "Usage: project delete");
        assertThat(service.listProjects()).isEmpty();
    }

    @Test
    void emptyListPrintsHelpfulMessage() {
        handler.handle(List.of("list"));
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("No projects found.");
    }

    @Test
    void addWithoutDescriptionThenShowDisplaysEmptyDescription() {
        handler.handle(List.of("ADD", "School"));
        var project = service.listProjects().get(0);
        assertThat(project.getDescription()).isEmpty();
        stdout.reset();
        handler.handle(List.of("show", project.getId().toString()));
        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .contains("ID:", project.getId().toString(), "Name:        School", "Description:");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void listIncludesDescriptionOnlyWhenPresent() {
        service.createProject("Alpha", "");
        service.createProject("Beta", "Coursework");
        handler.handle(List.of("list"));
        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .contains(" | Alpha", " | Beta | Coursework")
                .doesNotContain(" | Alpha | ");
    }

    @Test
    void editCanReplaceNameAndKeepDescription() {
        var project = service.createProject("Old", "Old description");
        handler.handle(List.of("EDIT", project.getId().toString(), "New"));
        assertThat(service.getProject(project.getId()).getName()).isEqualTo("New");
        assertThat(service.getProject(project.getId()).getDescription()).isEqualTo("Old description");
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Project updated:");
    }

    @Test
    void deleteRemovesExistingProject() {
        var project = service.createProject("Temporary", "");
        handler.handle(List.of("delete", project.getId().toString()));
        assertThat(service.listProjects()).isEmpty();
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Project deleted: " + project.getId());
    }

    @Test
    void unknownActionDoesNotModifyProjects() {
        handler.handle(List.of("rename", "Some project"));
        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("Unknown project action: 'rename'");
        assertThat(service.listProjects()).isEmpty();
    }

}
