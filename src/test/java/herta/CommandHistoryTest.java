package herta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Tests bounded command history without requiring JavaFX controls. */
class CommandHistoryTest {
    @Test
    void record_blankAndAdjacentDuplicateCommands_ignoresBoth() {
        CommandHistory history = new CommandHistory(3);
        history.record(" ");
        history.record("first");
        history.record("first");
        history.record("second");

        assertEquals(Optional.of("second"), history.previous("draft"));
        assertEquals(Optional.of("first"), history.previous("ignored"));
        assertEquals(Optional.of("second"), history.next());
        assertEquals(Optional.of("draft"), history.next());
    }

    @Test
    void record_atMaximumCapacity_keepsNewestCommands() {
        CommandHistory history = new CommandHistory(2);
        history.record("first");
        history.record("second");
        history.record("third");

        assertEquals(Optional.of("third"), history.previous("draft"));
        assertEquals(Optional.of("second"), history.previous("draft"));
        assertEquals(Optional.of("third"), history.next());
        assertEquals(Optional.of("draft"), history.next());
        assertEquals(Optional.empty(), history.next());
    }

    @Test
    void navigation_atBoundaries_returnsNoEntryWhenMovementIsUnavailable() {
        CommandHistory history = new CommandHistory(2);
        history.record("only");

        assertEquals(Optional.empty(), history.next());
        assertEquals(Optional.of("only"), history.previous("draft"));
        assertEquals(Optional.of("only"), history.previous("draft"));
        assertEquals(Optional.of("draft"), history.next());
    }

    @Test
    void navigation_afterPreviousRestoresDraftAtNewestPosition() {
        CommandHistory history = new CommandHistory(2);
        history.record("first");
        history.record("second");

        assertEquals(Optional.of("second"), history.previous("unfinished command"));
        assertEquals(Optional.of("first"), history.previous("ignored"));
        assertEquals(Optional.of("second"), history.next());
        assertEquals(Optional.of("unfinished command"), history.next());
    }

    @Test
    void record_afterNavigation_resetsNavigationToNewestCommand() {
        CommandHistory history = new CommandHistory(2);
        history.record("first");
        history.previous("draft");
        history.record("newest");

        assertEquals(Optional.empty(), history.next());
        assertEquals(Optional.of("newest"), history.previous("another draft"));
    }

    @Test
    void create_nonPositiveCapacity_rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new CommandHistory(0));
    }
}
