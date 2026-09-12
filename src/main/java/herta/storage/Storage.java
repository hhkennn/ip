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

import herta.exception.HertaException;
import herta.task.Deadline;
import herta.task.Event;
import herta.task.Task;
import herta.task.TaskList;
import herta.task.Todo;

/**
 * Handles loading tasks from and saving tasks to Herta's data file.
 */
public class Storage {
    private static final int TYPE_INDEX = 0;
    private static final int STATUS_INDEX = 1;
    private static final int DESCRIPTION_INDEX = 2;
    private static final int FIRST_DATE_INDEX = 3;
    private static final int SECOND_DATE_INDEX = 4;
    private static final String TODO_TYPE = "T";
    private static final String DEADLINE_TYPE = "D";
    private static final String EVENT_TYPE = "E";
    private static final String INCOMPLETE_STATUS = "0";
    private static final String COMPLETED_STATUS = "1";
    private static final int MINIMUM_PART_COUNT = 2;
    private static final int TODO_PART_COUNT = 3;
    private static final int DEADLINE_PART_COUNT = 4;
    private static final int EVENT_PART_COUNT = 5;
    private static final String ACTIVE_LOAD_PREFIX = "Failed to load tasks: ";
    private static final String ARCHIVE_LOAD_PREFIX = "Failed to load archived tasks: ";
    private static final String RECORD_LOAD_PREFIX = "Failed to load tasks ";

    private final Path dataFile;

    /**
     * Creates storage backed by the given file path.
     *
     * @param filePath the path of Herta's data file
     */
    public Storage(String filePath) {
        dataFile = Path.of(filePath);
    }

    /**
     * Returns the path managed by this storage.
     *
     * @return the configured data-file path
     */
    public Path getDataFile() {
        return dataFile;
    }

    /**
     * Resolves the archive path that belongs beside an active data file.
     *
     * @param activeFilePath the configured active data-file path
     * @return the archive path in the active file's parent directory
     */
    public static Path resolveArchivePath(String activeFilePath) {
        Path activePath = Path.of(activeFilePath);
        Path parent = activePath.getParent();
        if (parent == null) {
            parent = Path.of(".");
        }
        return parent.resolve("archive.txt");
    }

    /**
     * Rejects active and archive paths that identify the same file.
     *
     * @param activeFile the active-task path
     * @param archiveFile the archive-task path
     * @throws HertaException if the paths conflict or cannot be compared
     */
    public static void validateDistinctPaths(Path activeFile, Path archiveFile)
            throws HertaException {
        Path normalizedActivePath = activeFile.toAbsolutePath().normalize();
        Path normalizedArchivePath = archiveFile.toAbsolutePath().normalize();
        if (normalizedActivePath.equals(normalizedArchivePath)) {
            throw new HertaException(ARCHIVE_LOAD_PREFIX
                    + "active and archive paths must be different files.");
        }

        try {
            if (Files.exists(normalizedActivePath) && Files.exists(normalizedArchivePath)
                    && Files.isSameFile(normalizedActivePath, normalizedArchivePath)) {
                throw new HertaException(ARCHIVE_LOAD_PREFIX
                        + "active and archive paths must be different files.");
            }
        } catch (IOException | SecurityException e) {
            throw new HertaException(ARCHIVE_LOAD_PREFIX
                    + "unable to compare active and archive paths.");
        }
    }

    /**
     * Loads all saved tasks from the data file.
     *
     * <p>A missing data file represents a fresh start, so an empty task list
     * is returned in that case.</p>
     *
     * @return the task list reconstructed from the saved lines
     * @throws HertaException if the data file cannot be read or parsed
     */
    public TaskList load() throws HertaException {
        return loadWithPrefix(ACTIVE_LOAD_PREFIX, RECORD_LOAD_PREFIX);
    }

    /**
     * Loads archived tasks from this storage file.
     *
     * <p>A missing archive file represents an empty archive.</p>
     *
     * @return the archived task list reconstructed from saved lines
     * @throws HertaException if the archive cannot be read or parsed
     */
    public TaskList loadArchived() throws HertaException {
        return loadWithPrefix(ARCHIVE_LOAD_PREFIX, ARCHIVE_LOAD_PREFIX + RECORD_LOAD_PREFIX);
    }

    /**
     * Loads tasks while retaining the error wording appropriate to the file's role.
     *
     * @param loadPrefix the prefix for file-level load failures
     * @param recordPrefix the prefix for malformed-record failures
     * @return the loaded task list
     * @throws HertaException if the file cannot be read or parsed
     */
    private TaskList loadWithPrefix(String loadPrefix, String recordPrefix) throws HertaException {
        try {
            if (Files.notExists(dataFile)) {
                return new TaskList();
            }
            if (!Files.isRegularFile(dataFile)) {
                throw new HertaException(loadPrefix + "data path is not a regular file.");
            }

            return parseStorageLines(readStorageLines(), recordPrefix);
        } catch (HertaException e) {
            throw e;
        } catch (IOException | SecurityException e) {
            throw new HertaException(loadPrefix + e.getMessage());
        }
    }

    /**
     * Saves the complete in-memory task list to the data file.
     *
     * <p>The file is rewritten instead of appended to so that deletions and
     * completion-status changes are reflected in the saved data.</p>
     *
     * @param tasks the task list to save
     * @throws HertaException if the task list cannot be written
     */
    public void save(TaskList tasks) throws HertaException {
        List<String> lines = serializeTasks(tasks);
        writeStorageLines(lines);
    }

    /**
     * Stages and replaces two task files as one logical operation.
     *
     * <p>Both collections are serialized and validated before either target file is changed.
     * If either commit fails, the original contents or absence of both files are restored.</p>
     *
     * @param archiveStorage storage for the second task file
     * @param activeTasks the resulting active collection
     * @param archivedTasks the resulting archive collection
     * @param failurePrefix the user-facing prefix for persistence failures
     * @throws HertaException if validation, writing, replacing, or rollback fails
     */
    public void saveBoth(Storage archiveStorage, TaskList activeTasks,
                         TaskList archivedTasks, String failurePrefix) throws HertaException {
        FileSnapshot originalActiveFile = null;
        FileSnapshot originalArchiveFile = null;
        try {
            List<String> activeLines = serializeTasks(activeTasks);
            List<String> archiveLines = archiveStorage.serializeTasks(archivedTasks);
            originalActiveFile = FileSnapshot.capture(dataFile);
            originalArchiveFile = FileSnapshot.capture(archiveStorage.dataFile);
            writeAndReplaceBothFiles(archiveStorage, activeLines, archiveLines);
        } catch (HertaException e) {
            throw withFailurePrefix(failurePrefix, e.getMessage());
        } catch (IOException | SecurityException e) {
            String failureReason = e.getMessage();
            try {
                restoreOriginalFiles(originalActiveFile, originalArchiveFile);
            } catch (IOException | SecurityException rollbackError) {
                failureReason += "; rollback failed: " + rollbackError.getMessage();
            }
            throw withFailurePrefix(failurePrefix, failureReason);
        }
    }

    /**
     * Stages and replaces both serialized task files, cleaning up temporary files afterward.
     *
     * @param archiveStorage storage for the archive file
     * @param activeLines serialized active tasks
     * @param archiveLines serialized archived tasks
     * @throws IOException if either file cannot be staged or replaced
     */
    private void writeAndReplaceBothFiles(Storage archiveStorage, List<String> activeLines,
                                          List<String> archiveLines) throws IOException {
        Path temporaryActiveFile = null;
        Path temporaryArchiveFile = null;
        try {
            temporaryActiveFile = writeTemporaryFile(activeLines);
            temporaryArchiveFile = archiveStorage.writeTemporaryFile(archiveLines);
            replaceDataFile(temporaryActiveFile);
            temporaryActiveFile = null;
            archiveStorage.replaceDataFile(temporaryArchiveFile);
            temporaryArchiveFile = null;
        } finally {
            deleteTemporaryFile(temporaryActiveFile);
            deleteTemporaryFile(temporaryArchiveFile);
        }
    }

    /**
     * Restores both original files to their captured state.
     *
     * @param originalActiveFile the active file's captured state
     * @param originalArchiveFile the archive file's captured state
     * @throws IOException if either file cannot be restored
     */
    private void restoreOriginalFiles(FileSnapshot originalActiveFile,
                                      FileSnapshot originalArchiveFile) throws IOException {
        restoreFile(originalActiveFile);
        restoreFile(originalArchiveFile);
    }

    /**
     * Converts every task to a validated storage record.
     *
     * @param tasks the task list to serialize
     * @return validated storage records in task-list order
     * @throws HertaException if the task list or one of its records is invalid
     */
    private List<String> serializeTasks(TaskList tasks) throws HertaException {
        if (tasks == null) {
            throw new HertaException("Failed to save tasks: task list is null.");
        }

        List<String> lines = new ArrayList<>();
        for (Task task : tasks) {
            lines.add(serializeTask(task));
        }
        return lines;
    }

    /**
     * Converts and validates one task as a storage record.
     *
     * @param task the task to serialize
     * @return the validated storage record
     * @throws HertaException if the task or its record is invalid
     */
    private String serializeTask(Task task) throws HertaException {
        if (task == null) {
            throw new HertaException("Failed to save tasks: task list contains a null task.");
        }

        final String storageString;
        try {
            storageString = task.toStorageString();
        } catch (RuntimeException e) {
            throw new HertaException("Failed to save tasks: task contains invalid data.");
        }
        if (storageString == null) {
            throw new HertaException("Failed to save tasks: task contains invalid data.");
        }
        if (storageString.contains("\n") || storageString.contains("\r")) {
            throw new HertaException("Failed to save tasks: task fields cannot contain line breaks.");
        }
        try {
            Task parsedTask = parseStoredTask(storageString);
            assert parsedTask != null : "A validated storage record must produce a task.";
        } catch (HertaException e) {
            throw new HertaException("Failed to save tasks: " + e.getMessage());
        }
        return storageString;
    }

    /**
     * Writes storage records through a temporary file and replaces the data file.
     *
     * @param lines the records to write
     * @throws HertaException if the data file cannot be replaced
     */
    private void writeStorageLines(List<String> lines) throws HertaException {
        Path temporaryFile = null;
        try {
            temporaryFile = writeTemporaryFile(lines);
            replaceDataFile(temporaryFile);
        } catch (IOException | SecurityException e) {
            throw new HertaException("Failed to save tasks: " + e.getMessage());
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    /**
     * Writes validated lines to a temporary file beside this storage's target.
     *
     * @param lines the records to write
     * @return the temporary file path
     * @throws IOException if the directory or temporary file cannot be created
     */
    private Path writeTemporaryFile(List<String> lines) throws IOException {
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
     * Creates the data directory and verifies that the data path can be replaced.
     *
     * @return the directory containing the data file
     * @throws IOException if the data path is not a regular file or the directory
     *         cannot be created
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
     * Replaces the data file with a temporary file, falling back when atomic
     * replacement is unsupported by the file system.
     *
     * @param temporaryFile the temporary file containing the new records
     * @throws IOException if the replacement fails
     */
    private void replaceDataFile(Path temporaryFile) throws IOException {
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
    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile != null) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException | SecurityException ignored) {
                // The original data file is still preserved if cleanup fails.
            }
        }
    }

    /**
     * Reads the data file as strict UTF-8 instead of silently replacing malformed bytes.
     *
     * @return the lines from the data file
     * @throws IOException if the file cannot be read or is not valid UTF-8
     */
    private List<String> readStorageLines() throws IOException {
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
     * Reconstructs all tasks from the non-blank lines in the data file.
     *
     * @param lines the lines read from the data file
     * @return the reconstructed task list
     * @throws HertaException if a line contains an invalid task record
     */
    private TaskList parseStorageLines(List<String> lines, String recordPrefix) throws HertaException {
        TaskList tasks = new TaskList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            tasks.add(parseStorageLine(line, i + 1, recordPrefix));
        }
        return tasks;
    }

    /**
     * Parses one storage line and adds its source line number to any error.
     *
     * @param line one line in Herta's storage format
     * @param lineNumber the one-based source line number
     * @return the reconstructed task
     * @throws HertaException if the line does not contain a valid task record
     */
    private Task parseStorageLine(String line, int lineNumber, String recordPrefix)
            throws HertaException {
        try {
            Task task = parseStoredTask(line);
            assert task != null : "A valid storage line must produce a task.";
            return task;
        } catch (HertaException e) {
            throw new HertaException(recordPrefix + "at line "
                    + lineNumber + ": " + e.getMessage());
        }
    }

    /**
     * Restores one target file to its captured contents or captured absence.
     *
     * @param snapshot the target's original state
     * @throws IOException if restoration fails
     */
    private void restoreFile(FileSnapshot snapshot) throws IOException {
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

    /**
     * Adds the operation-specific prefix to a persistence failure.
     *
     * @param failurePrefix the required response prefix
     * @param reason the specific failure reason
     * @return the wrapped exception
     */
    private HertaException withFailurePrefix(String failurePrefix, String reason) {
        String safeReason = reason == null || reason.isBlank() ? "unknown persistence error" : reason;
        return new HertaException(failurePrefix + safeReason);
    }

    /**
     * Captures the exact bytes and existence state of one target file.
     *
     * @param path the target path
     * @return the captured file state
     * @throws IOException if the existing file cannot be read
     */
    private record FileSnapshot(Path path, boolean wasPresent, byte[] contents) {
        private static FileSnapshot capture(Path path) throws IOException {
            boolean wasPresent = Files.exists(path);
            byte[] contents = wasPresent ? Files.readAllBytes(path) : new byte[0];
            return new FileSnapshot(path, wasPresent, contents);
        }
    }

    /**
     * Reconstructs a task from one serialized data-file line.
     *
     * @param line one line in Herta's storage format
     * @return the reconstructed task
     * @throws HertaException if the line does not contain a supported task type
     */
    private Task parseStoredTask(String line) throws HertaException {
        String[] parts = splitStorageRecord(line);
        boolean isCompleted = isCompletedStorageRecord(parts);
        Task task = createTask(parts);
        if (isCompleted) {
            task.markAsDone();
        }
        return task;
    }

    /**
     * Splits a serialized task line into its storage fields.
     *
     * @param line one line in Herta's storage format
     * @return the normalized storage fields
     */
    private String[] splitStorageRecord(String line) {
        String normalizedLine = line.trim();
        if (normalizedLine.startsWith("\uFEFF")) {
            normalizedLine = normalizedLine.substring(1).trim();
        }
        return normalizedLine.split("\\s*\\|\\s*", -1);
    }

    /**
     * Validates a serialized task record and checks whether it is complete.
     *
     * @param parts the storage fields to validate
     * @return {@code true} if the record marks the task as complete
     * @throws HertaException if the record is malformed
     */
    private boolean isCompletedStorageRecord(String[] parts) throws HertaException {
        if (parts.length < MINIMUM_PART_COUNT) {
            throw new HertaException("Invalid saved task: missing task type or status.");
        }

        String type = parts[TYPE_INDEX];
        int expectedPartCount = getExpectedPartCount(type);
        validatePartCount(parts, type, expectedPartCount);
        validateStatus(parts[STATUS_INDEX]);
        validateTaskFields(parts);
        return COMPLETED_STATUS.equals(parts[STATUS_INDEX]);
    }

    /**
     * Returns the expected number of fields for a serialized task type.
     *
     * @param type the serialized task type
     * @return the expected field count
     * @throws HertaException if the task type is unsupported
     */
    private int getExpectedPartCount(String type) throws HertaException {
        return switch (type) {
            case TODO_TYPE -> TODO_PART_COUNT;
            case DEADLINE_TYPE -> DEADLINE_PART_COUNT;
            case EVENT_TYPE -> EVENT_PART_COUNT;
            default -> throw new HertaException("Invalid saved task: unknown task type '"
                    + type + "'.");
        };
    }

    /**
     * Checks that a serialized task contains the expected number of fields.
     *
     * @param parts the serialized task fields
     * @param type the serialized task type
     * @param expectedPartCount the expected field count
     * @throws HertaException if the field count is invalid
     */
    private void validatePartCount(String[] parts, String type, int expectedPartCount)
            throws HertaException {
        if (parts.length != expectedPartCount) {
            throw new HertaException("Invalid saved task: type " + type
                    + " requires " + expectedPartCount + " fields.");
        }
    }

    /**
     * Checks that a serialized task has a supported completion status.
     *
     * @param status the serialized completion status
     * @throws HertaException if the status is invalid
     */
    private void validateStatus(String status) throws HertaException {
        if (!INCOMPLETE_STATUS.equals(status) && !COMPLETED_STATUS.equals(status)) {
            throw new HertaException("Invalid saved task: completion status must be "
                    + INCOMPLETE_STATUS + " or " + COMPLETED_STATUS + ".");
        }
    }

    /**
     * Checks that serialized task fields after the status are non-blank.
     *
     * @param parts the serialized task fields
     * @throws HertaException if a task field is blank
     */
    private void validateTaskFields(String[] parts) throws HertaException {
        for (int i = DESCRIPTION_INDEX; i < parts.length; i++) {
            if (parts[i].isBlank()) {
                throw new HertaException("Invalid saved task: task fields cannot be blank.");
            }
        }
    }

    /**
     * Reconstructs a task from validated storage fields.
     *
     * @param parts the validated storage fields
     * @return the reconstructed task
     * @throws HertaException if the task type cannot be reconstructed
     */
    private Task createTask(String[] parts) throws HertaException {
        final Task task;
        try {
            task = switch (parts[TYPE_INDEX]) {
                case TODO_TYPE -> new Todo(parts[DESCRIPTION_INDEX]);
                case DEADLINE_TYPE -> Deadline.fromStorage(parts[DESCRIPTION_INDEX], parts[FIRST_DATE_INDEX]);
                case EVENT_TYPE -> Event.fromStorage(parts[DESCRIPTION_INDEX], parts[FIRST_DATE_INDEX],
                        parts[SECOND_DATE_INDEX]);
                default -> throw new HertaException("Invalid saved task: unknown task type '"
                        + parts[TYPE_INDEX] + "'.");
            };
        } catch (IllegalArgumentException e) {
            throw new HertaException(e.getMessage());
        }
        return task;
    }
}
