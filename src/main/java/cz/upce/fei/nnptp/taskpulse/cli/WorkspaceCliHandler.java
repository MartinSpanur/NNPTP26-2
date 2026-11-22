package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.storage.csv.WorkspaceBackup;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class WorkspaceCliHandler implements CliCommandHandler {
    static final String USAGE = "workspace <export|import> <path>";

    private final WorkspaceBackup backup;
    private final PrintStream out;

    WorkspaceCliHandler(WorkspaceBackup backup, PrintStream out) {
        this.backup = Objects.requireNonNull(backup);
        this.out = Objects.requireNonNull(out);
    }

    @Override
    public void handle(List<String> args) {
        if (args.size() != 2 || args.get(1).isBlank()) {
            throw new IllegalArgumentException("Usage: " + USAGE);
        }
        String action = args.get(0).toLowerCase(Locale.ROOT);
        if (!action.equals("export") && !action.equals("import")) {
            throw new IllegalArgumentException("Usage: " + USAGE);
        }
        Path path = Path.of(args.get(1));
        switch (action) {
            case "export" -> {
                backup.exportArchive(path);
                out.println("Workspace exported: " + path);
            }
            case "import" -> {
                backup.importBundle(path);
                out.println("Workspace imported: " + path);
            }
        }
    }
}
