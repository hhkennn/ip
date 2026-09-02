package herta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the application loop and startup handling for storage failures.
 */
class HertaTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void run_processesCommandsAndPersistsChanges() throws Exception {
        Path dataFile = temporaryDirectory.resolve("herta.txt");
        String output = runWithInput(dataFile, "todo read book\nbye\n");

        assertTrue(output.contains("Oh, you're here. I'm Herta."));
        assertTrue(output.contains("There. I've added it:"));
        assertTrue(output.contains("Leaving already? Goodbye."));
        assertEquals("T | 0 | read book", Files.readString(dataFile).trim());
    }

    @Test
    void run_reportsStorageLoadFailureAndStops() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("data-directory");
        Files.createDirectory(dataDirectory);

        String output = runWithInput(dataDirectory, "bye\n");

        assertTrue(output.contains("Failed to load tasks: data path is not a regular file."));
        assertFalse(output.contains("Your command?"));
    }

    @Test
    void getResponse_processesCommandAndReturnsOutput() throws Exception {
        Path dataFile = temporaryDirectory.resolve("herta.txt");
        Herta herta = new Herta(dataFile.toString());

        HertaResponse addResponse = herta.getResponse("todo read book");
        HertaResponse listResponse = herta.getResponse("list");
        HertaResponse invalidResponse = herta.getResponse("blah");
        HertaResponse goodbyeResponse = herta.getResponse("bye");

        assertTrue(addResponse.getMessage().contains("There. I've added it:"));
        assertTrue(listResponse.getMessage().contains("1.[T][ ] read book"));
        assertTrue(invalidResponse.getMessage().contains("That command is invalid."));
        assertTrue(goodbyeResponse.getMessage().contains("Leaving already? Goodbye."));
        assertFalse(addResponse.isExitRequested());
        assertFalse(invalidResponse.isExitRequested());
        assertTrue(goodbyeResponse.isExitRequested());
        assertEquals("T | 0 | read book", Files.readString(dataFile).trim());
    }

    private String runWithInput(Path dataPath, String input) throws Exception {
        java.io.InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        ByteArrayInputStream testInput = new ByteArrayInputStream(
                input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setIn(testInput);
            System.setOut(new PrintStream(output));
            new Herta(dataPath.toString()).run();
            return output.toString();
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
    }
}
