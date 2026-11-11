package cz.upce.fei.nnptp.taskpulse.cli;

import java.util.ArrayList;
import java.util.List;

public final class CommandTokenizer {
    private CommandTokenizer() {
    }

    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        if (line == null || line.isBlank()) {
            return tokens;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = ' ';
        boolean tokenStarted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '\\' && i + 1 < line.length()) {
                    char next = line.charAt(i + 1);

                    switch (next) {
                        case 'n' -> {
                            current.append('\n');
                            i++;
                        }
                        case 't' -> {
                            current.append('\t');
                            i++;
                        }
                        case '\\' -> {
                            current.append('\\');
                            i++;
                        }
                        default -> {
                            if (next == quoteChar) {
                                current.append(quoteChar);
                                i++;
                            } else {
                                current.append(c);
                            }
                        }
                    }
                } else if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuotes = true;
                    quoteChar = c;
                    tokenStarted = true;
                } else if (Character.isWhitespace(c)) {
                    if (tokenStarted) {
                        tokens.add(current.toString());
                        current.setLength(0);
                        tokenStarted = false;
                    }
                } else {
                    current.append(c);
                    tokenStarted = true;
                }
            }
        }

        if (tokenStarted) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}
