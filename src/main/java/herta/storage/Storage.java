package herta.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import herta.exception.HertaException;
import herta.task.TaskList;

/**
 * Coordinates loading and saving tasks while keeping file and format details delegated.
 */
public class Storage {
    private static final String ACTIVE_LOAD_PREFIX = "Failed to load tasks: ";
    private static final String ARCHIVE_LOAD_PREFIX = "Failed to load archived tasks: ";
    private static final String RECORD_LOAD_PREFIX = "Failed to load tasks ";

    private final Path dataFile;
    private final TaskStorageConverter converter;
    private final StorageFileManager fileManager;

    /**
     * Creates storage backed by the given file path.
     *
     * @param filePath the path of Herta's data file
     */
    public Storage(String filePath) {
        dataFile = Path.of(filePath);
        converter = new TaskStorageConverter();
        fileManager = new StorageFileManager(dataFile);
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
     * <p>A missing data file represents a fresh start, so an empty task list is returned.</p>
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
            if (fileManager.isMissing()) {
                return new TaskList();
            }
            if (!fileManager.isRegularFile()) {
                throw new HertaException(loadPrefix + "data path is not a regular file.");
            }
            return converter.parseStorageLines(fileManager.readLines(), recordPrefix);
        } catch (IOException | SecurityException e) {
            throw new HertaException(loadPrefix + e.getMessage());
        }
    }

    /**
     * Saves the complete in-memory task list to the data file.
     *
     * @param tasks the task list to save
     * @throws HertaException if the task list cannot be written
     */
    public void save(TaskList tasks) throws HertaException {
        List<String> lines = converter.serializeTasks(tasks);
        try {
            fileManager.writeLines(lines);
        } catch (IOException | SecurityException e) {
            throw new HertaException("Failed to save tasks: " + e.getMessage());
        }
    }

    /**
     * Stages and replaces the active and archived task files as one logical operation.
     *
     * <p>If either commit fails, the original contents or absence of both files are restored.</p>
     *
     * @param archiveStorage storage for the second task file
     * @param activeTasks the resulting active collection
     * @param archivedTasks the resulting archive collection
     * @param failurePrefix the user-facing prefix for persistence failures
     * @throws HertaException if validation, writing, replacing, or rollback fails
     */
    public void saveActiveAndArchivedTasks(Storage archiveStorage, TaskList activeTasks,
                                           TaskList archivedTasks, String failurePrefix)
            throws HertaException {
        StorageFileManager.FileSnapshot originalActiveFile = null;
        StorageFileManager.FileSnapshot originalArchiveFile = null;
        try {
            List<String> activeLines = converter.serializeTasks(activeTasks);
            List<String> archiveLines = archiveStorage.converter.serializeTasks(archivedTasks);
            originalActiveFile = fileManager.captureSnapshot();
            originalArchiveFile = archiveStorage.fileManager.captureSnapshot();
            writeAndReplaceBothFiles(archiveStorage, activeLines, archiveLines);
        } catch (HertaException e) {
            throw withFailurePrefix(failurePrefix, e.getMessage());
        } catch (IOException | SecurityException e) {
            String failureReason = e.getMessage();
            try {
                restoreOriginalFiles(archiveStorage, originalActiveFile, originalArchiveFile);
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
            temporaryActiveFile = fileManager.writeTemporaryFile(activeLines);
            temporaryArchiveFile = archiveStorage.fileManager.writeTemporaryFile(archiveLines);
            fileManager.replaceDataFile(temporaryActiveFile);
            temporaryActiveFile = null;
            archiveStorage.fileManager.replaceDataFile(temporaryArchiveFile);
            temporaryArchiveFile = null;
        } finally {
            fileManager.deleteTemporaryFile(temporaryActiveFile);
            archiveStorage.fileManager.deleteTemporaryFile(temporaryArchiveFile);
        }
    }

    /**
     * Restores both original files to their captured states.
     *
     * @param archiveStorage storage for the archive file
     * @param originalActiveFile the active file's captured state
     * @param originalArchiveFile the archive file's captured state
     * @throws IOException if either file cannot be restored
     */
    private void restoreOriginalFiles(Storage archiveStorage,
                                      StorageFileManager.FileSnapshot originalActiveFile,
                                      StorageFileManager.FileSnapshot originalArchiveFile)
            throws IOException {
        fileManager.restoreSnapshot(originalActiveFile);
        archiveStorage.fileManager.restoreSnapshot(originalArchiveFile);
    }

    /**
     * Adds the operation-specific prefix to a persistence failure.
     *
     * @param failurePrefix the required response prefix
     * @param reason the specific failure reason
     * @return the wrapped exception
     */
    private HertaException withFailurePrefix(String failurePrefix, String reason) {
        String safeReason = reason == null || reason.isBlank()
                ? "unknown persistence error" : reason;
        return new HertaException(failurePrefix + safeReason);
    }
}
