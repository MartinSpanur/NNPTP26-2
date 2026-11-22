package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.storage.StorageException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class CsvUtils {
    private static final int END_OF_STREAM = -1;

    private CsvUtils() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean mustQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!mustQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String formatRow(List<String> fields) {
        return fields.stream()
                .map(CsvUtils::escape)
                .collect(Collectors.joining(","));
    }

    public static List<List<String>> parse(Reader reader) throws IOException {
        BufferedReader bufferedReader = (reader instanceof BufferedReader br) ? br : new BufferedReader(reader);
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();

        boolean inQuotes = false;
        int current;

        while ((current = bufferedReader.read()) != END_OF_STREAM) {
            char ch = (char) current;

            if (inQuotes) {
                if (ch == '\"') {
                    bufferedReader.mark(1);
                    int next = bufferedReader.read();
                    if (next == '\"') {
                        currentField.append('\"');
                    } else {
                        inQuotes = false;
                        if (next != END_OF_STREAM) {
                            bufferedReader.reset();
                        }
                    }
                } else {
                    currentField.append(ch);
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                } else if (ch == '\r') {
                    bufferedReader.mark(1);
                    int next = bufferedReader.read();
                    if (next != '\n' && next != END_OF_STREAM) {
                        bufferedReader.reset();
                    }
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    rows.add(new ArrayList<>(currentRow));
                    currentRow.clear();
                } else if (ch == '\n') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    rows.add(new ArrayList<>(currentRow));
                    currentRow.clear();
                } else {
                    currentField.append(ch);
                }
            }
        }

        if (inQuotes) {
            throw new StorageException("Invalid CSV: unclosed double quote at end of file");
        }

        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString());
            rows.add(new ArrayList<>(currentRow));
        }

        return rows;
    }
}
