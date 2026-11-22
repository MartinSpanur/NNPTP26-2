package cz.upce.fei.nnptp.taskpulse.cli;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class CliOptions {
    private static final Set<String> FLAGS = Set.of("--overdue", "--exceeded");
    private static final Set<String> VALUE_OPTIONS = Set.of(
            "--project", "--state", "--priority", "--label",
            "--search", "--sort", "--from", "--to",
            "--author", "--desc"
    );

    private final Map<String, String> values = new HashMap<>();

    private CliOptions() { }

    static CliOptions parse(List<String> args, int startIndex) {
        CliOptions options = new CliOptions();
        for (int i = startIndex; i < args.size(); i++) {
            String argument = args.get(i);
            int equalsIndex = argument.indexOf('=');
            String name = (equalsIndex < 0 ? argument : argument.substring(0, equalsIndex))
                    .toLowerCase(Locale.ROOT);
            if (!name.startsWith("--")) {
                continue;
            }
            if (!VALUE_OPTIONS.contains(name) && !FLAGS.contains(name)) {
                throw new IllegalArgumentException("Unknown option: " + name);
            }

            if (equalsIndex >= 0) {
                if (VALUE_OPTIONS.contains(name)) {
                    options.values.put(name, argument.substring(equalsIndex + 1));
                }
            } else if (FLAGS.contains(name)) {
                if (name.equals("--exceeded") && i + 1 < args.size()
                        && !args.get(i + 1).startsWith("--")) {
                    options.values.put(name, args.get(++i));
                } else {
                    options.values.put(name, "");
                }
            } else {
                if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for option " + name);
                }
                options.values.put(name, args.get(++i));
            }
        }
        return options;
    }

    boolean has(String name) {
        return values.containsKey(name);
    }

    String get(String name) {
        return values.get(name);
    }

    String getOrDefault(String name, String defaultValue) {
        return values.getOrDefault(name, defaultValue);
    }
} 
