package cz.upce.fei.nnptp.taskpulse.storage.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CsvRoundTripTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "", "plain", " leading and trailing spaces ", "value,with,commas",
            "double\"quotes", "first\nsecond", "first\rsecond", "first\r\nsecond",
            "Příliš žluťoučký kůň úpěl ďábelské ódy",
            "Čeština: č, ř, š, ž, ě, ů, ť, ď, ň",
            "Українська: ї, є, і, ґ; Привіт, світе!",
            "中文：你好，世界", "日本語：こんにちは、世界", "한국어: 안녕하세요",
            "العربية: مرحبًا بالعالم", "مُرَحَّبًا بِكُمْ",
            "TaskPulse مهمة 42, \"مكتمل\"",
            "Café", "Cafe\u0301", "🚀 🔐",
            "\"", "\n", "\r", ","
    })
    void preservesSpecialCharactersInRoundTrip(String value) throws Exception {
        String csv = "prefix," + CsvUtils.escape(value) + "\n";

        assertThat(CsvUtils.parse(new StringReader(csv)))
                .containsExactly(List.of("prefix", value));
    }

    @Test
    void escapesDelimitersQuotesAndLineBreaks() {
        assertThat(CsvUtils.escape("plain")).isEqualTo("plain");
        assertThat(CsvUtils.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvUtils.escape("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(CsvUtils.escape("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(CsvUtils.escape("a\rb")).isEqualTo("\"a\rb\"");
    }

    @Test
    void preservesDeterministicallyGeneratedCsvRecords() throws Exception {
        Random random = new Random(20261109L);
        String alphabet = "abcXYZ09,\"\n\r\t éčї漢";

        for (int record = 0; record < 250; record++) {
            List<String> fields = new ArrayList<>();
            fields.add("record-" + record);
            int additionalFields = 1 + random.nextInt(5);
            for (int i = 0; i < additionalFields; i++) {
                int length = random.nextInt(21);
                StringBuilder field = new StringBuilder();
                for (int j = 0; j < length; j++) {
                    field.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
                fields.add(field.toString());
            }

            String csv = fields.stream()
                    .map(CsvUtils::escape)
                    .collect(Collectors.joining(",")) + "\n";

            assertThat(CsvUtils.parse(new StringReader(csv)))
                    .as("CSV round trip for generated record %s", record)
                    .containsExactly(fields);
        }
    }
}
