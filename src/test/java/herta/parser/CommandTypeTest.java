package herta.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests command recognition, including commands that do and do not accept arguments.
 */
class CommandTypeTest {

    @Test
    void fromInput_supportedCommands_returnsCorrespondingCommandType() {
        assertEquals(CommandType.TODO, CommandType.fromInput("  todo buy milk  "));
        assertEquals(CommandType.DEADLINE, CommandType.fromInput("deadline report /by 2019-10-15"));
        assertEquals(CommandType.EVENT, CommandType.fromInput("event meeting /from Mon /to Tue"));
        assertEquals(CommandType.LIST, CommandType.fromInput("list"));
        assertEquals(CommandType.BYE, CommandType.fromInput("bye"));
    }

    @Test
    void fromInput_commandLookalikes_returnsUnknown() {
        assertEquals(CommandType.UNKNOWN, CommandType.fromInput("todotask"));
        assertEquals(CommandType.UNKNOWN, CommandType.fromInput("list extra"));
        assertEquals(CommandType.UNKNOWN, CommandType.fromInput("byebye"));
        assertEquals(CommandType.UNKNOWN, CommandType.fromInput(""));
    }
}
