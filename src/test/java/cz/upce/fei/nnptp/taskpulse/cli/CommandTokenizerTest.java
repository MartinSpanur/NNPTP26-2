
package cz.upce.fei.nnptp.taskpulse.cli;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CommandTokenizerTest {

    @Test
    void preservesEmptyDoubleQuotedArgument() {
        List<String> actual = CommandTokenizer.tokenize(
                "project add \"\" \"Project description\"");

        assertThat(actual).containsExactly("project", "add", "", "Project description");
    }

    @Test
    void preservesEmptySingleQuotedArgument() {
        List<String> actual = CommandTokenizer.tokenize(
                "project add '' 'Project description'");

        assertThat(actual).containsExactly("project", "add", "", "Project description");
    }

    @Test
    void preservesEmptyArgumentAtStart() {
        List<String> actual = CommandTokenizer.tokenize("\"\" next");

        assertThat(actual).containsExactly("", "next");
    }

    @Test
    void preservesEmptyArgumentAtEnd() {
        List<String> actual = CommandTokenizer.tokenize("project add Name \"\"");

        assertThat(actual).containsExactly("project", "add", "Name", "");
    }

    @Test
    void preservesConsecutiveEmptyArguments() {
        List<String> actual = CommandTokenizer.tokenize("\"\" '' \"\"");

        assertThat(actual).containsExactly("", "", "");
    }

    @Test
    void whitespaceDoesNotCreateExtraEmptyArguments() {
        List<String> actual = CommandTokenizer.tokenize("  project\t add   \"\"   description  ");

        assertThat(actual).containsExactly("project", "add", "", "description");
        assertThat(CommandTokenizer.tokenize(" \t\n ")).isEmpty();
    }

    @Test
    void adjacentQuotedPartsRemainOneArgument() {
        List<String> actual = CommandTokenizer.tokenize("say pre\"\"fix \"\"''");

        assertThat(actual).containsExactly("say", "prefix", "");
    }

    @Test
    void convertsNewlineEscape() {
        List<String> actual = CommandTokenizer.tokenize(
                "say \"First\\nSecond\"");

        assertThat(actual).containsExactly("say", "First\nSecond");
    }

    @Test
    void convertsTabEscape() {
        List<String> actual = CommandTokenizer.tokenize(
                "say \"First\\tSecond\"");

        assertThat(actual).containsExactly("say", "First\tSecond");
    }

    @Test
    void convertsBackslashEscape() {
        List<String> actual = CommandTokenizer.tokenize(
                "note \"C:\\\\Temp\\\\report\"");

        assertThat(actual).containsExactly("note", "C:\\Temp\\report");
    }

    @Test
    void preservesEscapedQuotes() {
        List<String> actual = CommandTokenizer.tokenize(
                "say \"Hello \\\"world\\\"\"");

        assertThat(actual).containsExactly("say", "Hello \"world\"");
    }

    @Test
    void preservesEscapedSingleQuotes() {
        List<String> actual = CommandTokenizer.tokenize(
                "say 'It\\'s working'");

        assertThat(actual).containsExactly("say", "It's working");
    }

    @Test
    void preservesUnknownEscapes() {
        List<String> actual = CommandTokenizer.tokenize(
                "say \"text\\xmore\"");

        assertThat(actual).containsExactly("say", "text\\xmore");
    }

    @Test
    void supportsSingleQuotedArguments() {
        List<String> actual = CommandTokenizer.tokenize(
                "say 'First\\nSecond'");

        assertThat(actual).containsExactly("say", "First\nSecond");
    }

    @Test
    void doesNotConvertEscapesOutsideQuotes() {
        List<String> actual = CommandTokenizer.tokenize(
                "say First\\nSecond");

        assertThat(actual).containsExactly("say", "First\\nSecond");
    }

    @Test
    void handlesMultipleEscapesInOneArgument() {
        List<String> actual = CommandTokenizer.tokenize(
                "say \"First\\nSecond\\tEnd\"");

        assertThat(actual).containsExactly("say", "First\nSecond\tEnd");
    }
}
