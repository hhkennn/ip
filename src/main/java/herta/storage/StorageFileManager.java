package herta.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages strict file reads and replace-style writes for one storage file.
 *
 * <p>The manager also captures and restores file state for multi-file rollback.</p>
 */
final class StorageFileManager {
    private final Path dataFile;

    /**
     * Creates a manager for a storage file.
     *
     * @param dataFile the file to manage
     */
    StorageFileManager(Path dataFile) {
        this.dataFile = dataFile;
    }

    /**
     * Checks whether this storage file is absent.
     *
     * @return {@code true} if the file does not exist
     */
    boolean isMissing() {
        return Files.notExists(dataFile);
    }

    /**
     * Checks whether this storage path is a regular file.
     *
     * @return {@code true} if the path is a regular file
     */
    boolean isRegularFile() {
        return Files.isRegularFile(dataFile);
    }

    /**
     * Reads the data file as strict UTF-8 instead of silently replacing malformed bytes.
     *
     * @return the lines from the data file
     * @throws IOException if the file cannot be read or is not valid UTF-8
     */
    List<String> readLines() throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(dataFile), decoder))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Writes lines through a temporary file and replaces the data file.
     *
     * @param lines the records to write
     * @throws IOException if the data file cannot be replaced
     */
    void writeLines(List<String> lines) throws IOException {
        Path temporaryFile = null;
        try {
            temporaryFile = writeTemporaryFile(lines);
            replaceDataFile(temporaryFile);
            temporaryFile = null;
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    /**
     * Writes validated lines to a temporary file beside the target.
     *
     * @param lines the records to stage
     * @return the temporary file path
     * @throws IOException if the directory or temporary file cannot be created
     */
    Path writeTemporaryFile(List<String> lines) throws IOException {
        Path dataDirectory = prepareDataDirectory();
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(dataDirectory, ".herta-", ".tmp");
            Files.write(temporaryFile, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return temporaryFile;
        } catch (IOException | SecurityException e) {
            deleteTemporaryFile(temporaryFile);
            throw e;
        }
    }

    /**
     * Creates the data directory and verifies that the target can be replaced.
     *
     * @return the directory containing the data file
     * @throws IOException if the target is not a regular file or the directory cannot be created
     */
    private Path prepareDataDirectory() throws IOException {
        Path dataDirectory = dataFile.getParent();
        if (dataDirectory == null) {
            dataDirectory = Path.of(".");
        }
        Files.createDirectories(dataDirectory);
        if (Files.exists(dataFile) && !Files.isRegularFile(dataFile)) {
            throw new IOException("data path is not a regular file");
        }
        return dataDirectory;
    }

    /**
     * Replaces the target with a temporary file, falling back when atomic replacement is unsupported.
     *
     * @param temporaryFile the temporary file containing the new records
     * @throws IOException if the replacement fails
     */
    void replaceDataFile(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, dataFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException e) {
            // Fall back only when atomic replacement is unsupported or rejected for the target.
            Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Removes a temporary file after a save attempt.
     *
     * @param temporaryFile the temporary file to remove, if one was created
     */
    void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile != null) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException | SecurityException ignored) {
                // The original data file is still preserved if cleanup fails.
            }
        }
    }

    /**
     * Captures the exact bytes and existence state of the target file.
     *
     * @return the captured file state
     * @throws IOException if the existing file cannot be read
     */
    FileSnapshot captureSnapshot() throws IOException {
        boolean wasPresent = Files.exists(dataFile);
        byte[] contents = wasPresent ? Files.readAllBytes(dataFile) : new byte[0];
        return new FileSnapshot(dataFile, wasPresent, contents);
    }

    /**
     * Restores a target file to its captured contents or captured absence.
     *
     * @param snapshot the target's original state
     * @throws IOException if restoration fails
     */
    void restoreSnapshot(FileSnapshot snapshot) throws IOException {
        if (snapshot == null) {
            return;
        }
        if (!snapshot.wasPresent()) {
            Files.deleteIfExists(snapshot.path());
            return;
        }
        Files.write(snapshot.path(), snapshot.contents(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /** Captures the state needed to restore one file after a failed multi-file save. */
    record FileSnapshot(Path path, boolean wasPresent, byte[] contents) {
    }
}
