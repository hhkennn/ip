package herta.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import herta.exception.HertaException;

/** Tests the command-boundary normalization and character policy. */
class CommandTokenizerTest {

    @Test
    void normalize_outerWhitespaceAndKeywordSeparator_returnsStableCommand() throws Exception {
        assertEquals("todo read   book", CommandTokenizer.normalize(" \t todo\t read   book \t"));
    }

    @Test
    void normalize_nullAndUnsupportedCharacters_reportsSyntaxGuidance() {
        assertThrows(HertaException.class, () -> CommandTokenizer.normalize(null));
        assertThrows(HertaException.class, () -> CommandTokenizer.normalize("todo read\nbook"));
        assertThrows(HertaException.class, () -> CommandTokenizer.normalize("todo\u2003read"));
    }
}
