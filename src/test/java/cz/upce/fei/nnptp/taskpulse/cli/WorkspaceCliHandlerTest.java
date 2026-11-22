package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import cz.upce.fei.nnptp.taskpulse.storage.csv.WorkspaceBackup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceCliHandlerTest {
    @TempDir
    Path directory;

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private final TaskPulseCli cli = new TaskPulseCli(
            new ProjectService(projects, tasks), new TaskService(tasks, projects),
            new WorkspaceBackup(projects, tasks),
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(errors, true, StandardCharsets.UTF_8));

    @Test
    void exportsAndImportsCurrentSessionThroughTokenDispatch() {
        cli.executeTokens(List.of("project", "add", "Saved"));
        Path archive = directory.resolve("with spaces.zip");
        cli.executeTokens(List.of("workspace", "export", archive.toString()));
        cli.executeTokens(List.of("project", "add", "Discarded"));
        cli.executeTokens(List.of("workspace", "import", archive.toString()));

        assertThat(projects.findAll()).extracting(Project::getName).containsExactly("Saved");
        assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Workspace exported", "Workspace imported");
    }

    @Test
    void parsesQuotedPathsAndCaseInsensitiveActions() {
        Path archive = directory.resolve("backup with spaces.zip");
        cli.executeLine("project add Saved");
        cli.executeLine("workspace EXPORT \"" + archive + "\"");
        cli.executeLine("project add Discarded");
        cli.executeLine("workspace IMPORT \"" + archive + "\"");

        assertThat(archive).exists();
        assertThat(projects.findAll()).extracting(Project::getName).containsExactly("Saved");
        assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"workspace", "workspace export", "workspace import",
            "workspace unknown backup.zip", "workspace export backup.zip extra",
            "workspace export \"\"", "workspace import \" \""})
    void rejectsInvalidArguments(String command) {
        cli.executeLine(command);

        assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(errors.toString(StandardCharsets.UTF_8))
                .contains("Invalid argument:", "Usage: workspace");
    }

    @Test
    void reportsStorageErrorsWithoutSuccessMessageOrMutation() {
        projects.save(new Project("Keep me", ""));
        cli.executeTokens(List.of("workspace", "import", directory.resolve("missing.zip").toString()));

        assertThat(projects.findAll()).extracting(Project::getName).containsExactly("Keep me");
        assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(errors.toString(StandardCharsets.UTF_8)).contains("Error: Failed to import workspace");
    }

    @Test
    void refusesToOverwriteArchiveThroughCli() throws Exception {
        Path archive = directory.resolve("existing.zip");
        Files.writeString(archive, "Keep original");
        cli.executeTokens(List.of("workspace", "export", archive.toString()));

        assertThat(Files.readString(archive)).isEqualTo("Keep original");
        assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(errors.toString(StandardCharsets.UTF_8)).contains("Error: Failed to export workspace");
    }

    @Test
    void helpIncludesWorkspaceAndExistingReportCommands() {
        cli.executeLine("help");

        assertThat(output.toString(StandardCharsets.UTF_8)).contains(
                "workspace export <archive.zip>",
                "workspace import <archive.zip|csv-directory>", "report export");
        assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
    }
}
