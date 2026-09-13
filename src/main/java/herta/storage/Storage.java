package herta.storage;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import herta.exception.HertaException;
import herta.task.TaskList;

/**
 * Coordinates loading and saving tasks while keeping file and format details delegated.
 */
public class Storage {
    private static final String ACTIVE_LOAD_PREFIX = "Failed to load tasks: ";
    private static final String ARCHIVE_LOAD_PREFIX = "Failed to load archived tasks: ";
    private static final String RECORD_LOAD_PREFIX = "Failed to load tasks ";
    private static final String ARCHIVE_RECORD_LOAD_PREFIX = "Failed to load archived tasks ";
    private static final String EXTERNAL_CHANGE_ERROR = "the data file changed outside Herta; "
            + "reload before saving.";
    private static final Logger LOGGER = Logger.getLogger(Storage.class.getName());

    private final Path dataFile;
    private final TaskStorageConverter converter;
    private final StorageFileManager fileManager;
    private StorageFileManager.FileSnapshot lastKnownSnapshot;
    private boolean isConsistent = true;
    private PersistenceState lastPersistenceState = PersistenceState.NOT_ATTEMPTED;

    /** Stores all data captured before a two-file persistence transaction begins. */
    private record StorageTransactionPlan(List<String> activeLines, List<String> archiveLines,
                                          StorageFileManager.FileSnapshot originalActiveFile,
                                          StorageFileManager.FileSnapshot originalArchiveFile,
                                          StorageTransactionJournal transactionJournal) {
    }

    /** Stores a rollback message together with whether cleanup is safe. */
    private record RollbackResult(String failureReason, boolean wasSuccessful) {
    }

    /**
     * Creates storage backed by the given file path.
     *
     * @param filePath the path of Herta's data file
     */
    public Storage(String filePath) {
        try {
            dataFile = Path.of(Objects.requireNonNull(filePath));
        } catch (InvalidPathException | NullPointerException e) {
            throw new IllegalArgumentException("Configured data path is invalid.", e);
        }
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

    /** Resets the save outcome before a new command is parsed or executed. */
    public void resetPersistenceState() {
        lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
    }

    /** Returns the outcome of the most recent save attempt. */
    public PersistenceState getLastPersistenceState() {
        return lastPersistenceState;
    }

    /**
     * Resolves the archive path that belongs beside an active data file.
     *
     * @param activeFilePath the configured active data-file path
     * @return the archive path in the active file's parent directory
     */
    public static Path resolveArchivePath(String activeFilePath) {
        Path activePath;
        try {
            activePath = Path.of(Objects.requireNonNull(activeFilePath));
        } catch (InvalidPathException | NullPointerException e) {
            throw new IllegalArgumentException("Configured data path is invalid.", e);
        }
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
        Objects.requireNonNull(activeFile, "The active data path cannot be null.");
        Objects.requireNonNull(archiveFile, "The archive data path cannot be null.");
        Path normalizedActivePath = activeFile.toAbsolutePath().normalize();
        Path normalizedArchivePath = archiveFile.toAbsolutePath().normalize();
        if (normalizedActivePath.equals(normalizedArchivePath)) {
            throw new HertaException(ARCHIVE_LOAD_PREFIX
                    + "active and archive paths must be different files.");
        }

        try {
            boolean bothPathsExist = Files.exists(normalizedActivePath)
                    && Files.exists(normalizedArchivePath);
            if (bothPathsExist && Files.isSameFile(normalizedActivePath, normalizedArchivePath)) {
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
        return loadWithPrefix(ARCHIVE_LOAD_PREFIX, ARCHIVE_RECORD_LOAD_PREFIX);
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
            StorageFileManager.PathStatus pathStatus = fileManager.getPathStatus();
            if (pathStatus == StorageFileManager.PathStatus.MISSING) {
                lastKnownSnapshot = fileManager.captureSnapshot();
                return new TaskList();
            }
            if (pathStatus == StorageFileManager.PathStatus.DIRECTORY
                    || pathStatus == StorageFileManager.PathStatus.OTHER) {
                throw new HertaException(loadPrefix + "data path is not a regular file.");
            }
            if (pathStatus == StorageFileManager.PathStatus.INACCESSIBLE) {
                throw new HertaException(loadPrefix + "data path is inaccessible.");
            }
            TaskList loadedTasks = converter.parseStorageLines(fileManager.readLines(), recordPrefix);
            lastKnownSnapshot = fileManager.captureSnapshot();
            return loadedTasks;
        } catch (HertaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            logStorageFailure("Unable to load storage.", e);
            throw new HertaException(loadPrefix + mapFailure(e));
        }
    }

    /**
     * Saves the complete in-memory task list to the data file.
     *
     * @param tasks the task list to save
     * @throws HertaException if the task list cannot be written
     */
    public void save(TaskList tasks) throws HertaException {
        lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
        List<String> lines = converter.serializeTasks(tasks);
        try (StorageFileLock ignored = StorageFileLock.acquire(dataFile)) {
            saveWhileLocked(lines);
        } catch (IOException | RuntimeException e) {
            logStorageFailure("Unable to acquire the storage lock.", e);
            throw new HertaException("Failed to save tasks: " + mapFailure(e));
        }
    }

    /** Saves one file while the adjacent lock excludes cooperating writers. */
    private void saveWhileLocked(List<String> lines) throws HertaException {
        ensureConsistent();
        StorageFileManager.FileSnapshot originalSnapshot = null;
        boolean wasReplacementStarted = false;
        Path temporaryFile = null;
        try {
            lastPersistenceState = PersistenceState.IN_PROGRESS;
            originalSnapshot = fileManager.captureSnapshot();
            initializeExpectedSnapshot(originalSnapshot);
            ensureHasNotChanged();
            temporaryFile = fileManager.writeTemporaryFile(lines);
            ensureHasNotChanged();
            wasReplacementStarted = true;
            fileManager.replaceDataFile(temporaryFile);
            temporaryFile = null;
            lastKnownSnapshot = fileManager.captureSnapshot();
            lastPersistenceState = PersistenceState.COMMITTED;
        } catch (IOException | RuntimeException e) {
            handleSingleFileSaveFailure(e, wasReplacementStarted, originalSnapshot, temporaryFile);
            throw new HertaException("Failed to save tasks: " + mapFailure(e));
        }
    }

    /** Restores and cleans up after an unexpected single-file save failure. */
    private void handleSingleFileSaveFailure(Exception failure, boolean wasReplacementStarted,
                                             StorageFileManager.FileSnapshot originalSnapshot,
                                             Path temporaryFile) throws HertaException {
        try {
            if (wasReplacementStarted) {
                restoreAfterFailedSave(originalSnapshot);
            } else {
                lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
            }
        } finally {
            fileManager.deleteTemporaryFile(temporaryFile);
        }
        logStorageFailure("Unable to save storage.", failure);
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
        Objects.requireNonNull(archiveStorage, "Archive storage cannot be null.");
        Objects.requireNonNull(failurePrefix, "A storage failure prefix cannot be null.");
        lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
        archiveStorage.lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
        StorageTransactionPlan transactionPlan = null;
        try (StorageFileLock ignored = StorageFileLock.acquire(dataFile, archiveStorage.dataFile)) {
            lastPersistenceState = PersistenceState.IN_PROGRESS;
            archiveStorage.lastPersistenceState = PersistenceState.IN_PROGRESS;
            transactionPlan = prepareTransaction(archiveStorage, activeTasks, archivedTasks);
            commitTransaction(archiveStorage, transactionPlan);
        } catch (HertaException e) {
            if (transactionPlan == null) {
                lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
                archiveStorage.lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
            }
            cleanUpTransaction(transactionPlan);
            throw withFailurePrefix(failurePrefix, e.getMessage());
        } catch (IOException | RuntimeException e) {
            RollbackResult rollbackResult = rollbackTransaction(archiveStorage, transactionPlan,
                    mapFailure(e));
            if (rollbackResult.wasSuccessful()) {
                cleanUpTransaction(transactionPlan);
            }
            throw withFailurePrefix(failurePrefix, rollbackResult.failureReason());
        }
    }

    /** Validates both collections and records their original state before committing a transaction. */
    private StorageTransactionPlan prepareTransaction(Storage archiveStorage, TaskList activeTasks,
                                                      TaskList archivedTasks)
            throws HertaException, IOException {
        ensureConsistent();
        archiveStorage.ensureConsistent();
        List<String> activeLines = converter.serializeTasks(activeTasks);
        List<String> archiveLines = archiveStorage.converter.serializeTasks(archivedTasks);
        StorageFileManager.FileSnapshot originalActiveFile = fileManager.captureSnapshot();
        StorageFileManager.FileSnapshot originalArchiveFile = archiveStorage.fileManager.captureSnapshot();
        initializeExpectedSnapshot(originalActiveFile);
        archiveStorage.initializeExpectedSnapshot(originalArchiveFile);
        ensureHasNotChanged();
        archiveStorage.ensureHasNotChanged();
        StorageTransactionJournal transactionJournal = StorageTransactionJournal.prepare(dataFile,
                archiveStorage.dataFile, originalActiveFile, originalArchiveFile);
        return new StorageTransactionPlan(activeLines, archiveLines, originalActiveFile,
                originalArchiveFile, transactionJournal);
    }

    /** Commits both replacement files and updates the snapshots used by future saves. */
    private void commitTransaction(Storage archiveStorage, StorageTransactionPlan transactionPlan)
            throws IOException {
        writeAndReplaceBothFiles(archiveStorage, transactionPlan.activeLines(),
                transactionPlan.archiveLines(), transactionPlan.transactionJournal(),
                transactionPlan.originalActiveFile(), transactionPlan.originalArchiveFile());
        lastKnownSnapshot = fileManager.captureSnapshot();
        archiveStorage.lastKnownSnapshot = archiveStorage.fileManager.captureSnapshot();
        lastPersistenceState = PersistenceState.COMMITTED;
        archiveStorage.lastPersistenceState = PersistenceState.COMMITTED;
        transactionPlan.transactionJournal().cleanUp();
    }

    /** Restores the captured state after a failed two-file commit. */
    private RollbackResult rollbackTransaction(Storage archiveStorage,
                                               StorageTransactionPlan transactionPlan,
                                               String failureReason) {
        if (transactionPlan == null) {
            lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
            archiveStorage.lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
            return new RollbackResult(failureReason, true);
        }
        try {
            restoreOriginalFiles(archiveStorage, transactionPlan.originalActiveFile(),
                    transactionPlan.originalArchiveFile());
            lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
            archiveStorage.lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
            return new RollbackResult(failureReason, true);
        } catch (IOException | RuntimeException rollbackError) {
            String recoveryMessage = failureReason + "; rollback failed; recovery journal retained at "
                    + transactionPlan.transactionJournal().getRecoveryLocation() + ".";
            logStorageFailure("Rollback failed while restoring storage.", rollbackError);
            isConsistent = false;
            archiveStorage.isConsistent = false;
            lastPersistenceState = PersistenceState.UNKNOWN;
            archiveStorage.lastPersistenceState = PersistenceState.UNKNOWN;
            return new RollbackResult(recoveryMessage, false);
        }
    }

    /** Removes transaction files once a transaction no longer needs recovery. */
    private void cleanUpTransaction(StorageTransactionPlan transactionPlan) {
        if (transactionPlan != null) {
            transactionPlan.transactionJournal().cleanUp();
        }
    }

    /** Restores the original file if an unexpected failure occurs after replacement. */
    private void restoreAfterFailedSave(StorageFileManager.FileSnapshot originalSnapshot)
            throws HertaException {
        try {
            fileManager.restoreSnapshot(originalSnapshot);
            lastKnownSnapshot = originalSnapshot;
            lastPersistenceState = PersistenceState.NOT_ATTEMPTED;
        } catch (IOException | RuntimeException rollbackError) {
            isConsistent = false;
            lastPersistenceState = PersistenceState.UNKNOWN;
            logStorageFailure("Rollback failed while restoring storage.", rollbackError);
            throw new HertaException("Failed to save tasks: storage consistency is uncertain; "
                    + "restart Herta before writing again.");
        }
    }

    /** Records the first known state used for detecting edits made by another process. */
    private void initializeExpectedSnapshot(StorageFileManager.FileSnapshot currentSnapshot) {
        if (lastKnownSnapshot == null) {
            lastKnownSnapshot = currentSnapshot;
        }
    }

    /** Prevents further writes after a rollback failure until the next startup recovery. */
    private void ensureConsistent() throws HertaException {
        if (!isConsistent) {
            throw new HertaException("Storage consistency is uncertain; restart Herta to recover "
                    + "the preserved transaction files before writing again.");
        }
    }

    /** Aborts a save when the file no longer matches the state Herta loaded. */
    private void ensureHasNotChanged() throws IOException {
        if (!fileManager.hasSameContents(lastKnownSnapshot)) {
            throw new IOException(EXTERNAL_CHANGE_ERROR);
        }
    }

    /** Converts platform-specific I/O details into stable user-facing wording. */
    private String mapFailure(Exception exception) {
        if (exception instanceof AccessDeniedException || exception instanceof SecurityException) {
            return "permission denied.";
        }
        if (exception instanceof NoSuchFileException) {
            return "data file is missing.";
        }
        if (exception instanceof FileSystemException fileSystemException
                && fileSystemException.getReason() != null
                && fileSystemException.getReason().toLowerCase().contains("lock")) {
            return "data file is locked.";
        }
        String reason = exception.getMessage();
        if (reason != null && reason.toLowerCase().contains("exceeds")) {
            return "data file is too large.";
        }
        if (reason != null && reason.toLowerCase().contains("space")) {
            return "disk is full.";
        }
        if (reason != null && reason.contains(EXTERNAL_CHANGE_ERROR)) {
            return EXTERNAL_CHANGE_ERROR;
        }
        return "an I/O failure occurred.";
    }

    /** Logs technical storage details without exposing platform paths or exception text. */
    private void logStorageFailure(String message, Exception exception) {
        LOGGER.log(Level.WARNING, message, exception);
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
                                          List<String> archiveLines,
                                          StorageTransactionJournal transactionJournal,
                                          StorageFileManager.FileSnapshot originalActiveFile,
                                          StorageFileManager.FileSnapshot originalArchiveFile)
            throws IOException {
        Path temporaryActiveFile = null;
        Path temporaryArchiveFile = null;
        try {
            temporaryActiveFile = fileManager.writeTemporaryFile(activeLines);
            temporaryArchiveFile = archiveStorage.fileManager.writeTemporaryFile(archiveLines);
            ensureHasNotChanged();
            archiveStorage.ensureHasNotChanged();
            fileManager.replaceDataFile(temporaryActiveFile);
            temporaryActiveFile = null;
            transactionJournal.markActiveCommitted(dataFile, archiveStorage.dataFile,
                    originalActiveFile, originalArchiveFile);
            archiveStorage.ensureHasNotChanged();
            archiveStorage.fileManager.replaceDataFile(temporaryArchiveFile);
            temporaryArchiveFile = null;
            transactionJournal.markCommitted(dataFile, archiveStorage.dataFile,
                    originalActiveFile, originalArchiveFile);
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
