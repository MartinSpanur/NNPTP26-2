package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.storage.StorageException;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvUtilsTest {
    @ParameterizedTest
    @ValueSource(strings = {"Co\"ur\"se", "field,Co\"ur\"se,next", " \"Course\"", "\t\"Course\""})
    void rejectsQuoteInsideUnquotedField(String csv) {
        assertThatThrownBy(() -> CsvUtils.parse(new StringReader(csv)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("double quote must appear at the start of a field");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"Course\"oops", "field,\"Course\"oops,next", "\"\"oops",
            "\"Course\" ", "\"Course\"\t", "\"first\nsecond\"x"
    })
    void rejectsTextAfterClosingQuote(String csv) {
        assertThatThrownBy(() -> CsvUtils.parse(new StringReader(csv)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("expected a comma or line ending after closing quote");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\n", "\r", "\r\n"})
    void preservesValidQuotedFieldsAtRecordEnd(String ending) throws Exception {
        String csv = "\"\",plain,\"a,b\",\"a\"\"b\",\"first\nsecond\",\"\"" + ending;

        assertThat(CsvUtils.parse(new StringReader(csv)))
                .containsExactly(List.of("", "plain", "a,b", "a\"b", "first\nsecond", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r", "\r\n"})
    void acceptsQuotedFieldsAcrossRecords(String ending) throws Exception {
        String csv = "\"first\",\"\"" + ending + "\"second\",plain";

        assertThat(CsvUtils.parse(new StringReader(csv)))
                .containsExactly(List.of("first", ""), List.of("second", "plain"));
    }

    @Test
    void rejectsUnclosedQuoteAtEndOfCSVFile() {
        String csv = "name,description\nProject,\"unfinished";
        
        assertThatThrownBy(() -> CsvUtils.parse(new StringReader(csv)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("unclosed double quote");
    }
    
    @Test
    void acceptsClosedQuoteAtEndOfCSVFile() {
        String csv = "name,description\nProject,\"finished\"";
        
        assertThatCode(() -> CsvUtils.parse(new StringReader(csv)))
                .doesNotThrowAnyException();
    }

    @Test
    void formatRowEscapesOnlyFieldsThatNeedQuoting() {
        String row = CsvUtils.formatRow(List.of("plain", "a,b", "say \"hi\"", ""));

        assertThat(row).isEqualTo("plain,\"a,b\",\"say \"\"hi\"\"\",");
    }
}
