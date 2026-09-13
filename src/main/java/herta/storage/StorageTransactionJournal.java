package herta.storage;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import herta.exception.HertaException;

/** Provides durable preparation and recovery for the active/archive two-file transaction. */
final class StorageTransactionJournal {
    private static final String JOURNAL_FILE_NAME = ".herta-transaction";
    private static final String ACTIVE_PATH_KEY = "activePath";
    private static final String ARCHIVE_PATH_KEY = "archivePath";
    private static final String ACTIVE_PRESENT_KEY = "activePresent";
    private static final String ARCHIVE_PRESENT_KEY = "archivePresent";
    private static final String ACTIVE_BACKUP_KEY = "activeBackup";
    private static final String ARCHIVE_BACKUP_KEY = "archiveBackup";
    private static final String PHASE_KEY = "phase";
    private static final String PREPARED_PHASE = "PREPARED";
    private static final String ACTIVE_COMMITTED_PHASE = "ACTIVE_COMMITTED";
    private static final String COMMITTED_PHASE = "COMMITTED";
    private static final String ABSENT_BACKUP = "ABSENT";
    private static final Set<String> JOURNAL_KEYS = Set.of(ACTIVE_PATH_KEY, ARCHIVE_PATH_KEY,
            ACTIVE_PRESENT_KEY, ARCHIVE_PRESENT_KEY, ACTIVE_BACKUP_KEY, ARCHIVE_BACKUP_KEY,
            PHASE_KEY);
    private static final Duration STALE_FILE_AGE = Duration.ofDays(1);
    private static final Logger LOGGER = Logger.getLogger(StorageTransactionJournal.class.getName());

    private final Path journalPath;
    private final Path activeBackup;
    private final Path archiveBackup;

    private StorageTransactionJournal(Path journalPath, Path activeBackup, Path archiveBackup) {
        this.journalPath = journalPath;
        this.activeBackup = activeBackup;
        this.archiveBackup = archiveBackup;
    }

    /**
     * Creates forced backup files and records a prepared transaction.
     *
     * @param activeFile the active data path
     * @param archiveFile the archive data path
     * @param activeSnapshot the active file state before replacement
     * @param archiveSnapshot the archive file state before replacement
     * @return the prepared journal
     * @throws IOException if a backup or the journal cannot be written
     */
    static StorageTransactionJournal prepare(Path activeFile, Path archiveFile,
                                             StorageFileManager.FileSnapshot activeSnapshot,
                                             StorageFileManager.FileSnapshot archiveSnapshot)
            throws IOException {
        Path directory = activeFile.toAbsolutePath().normalize().getParent();
        if (directory == null) {
            directory = Path.of(".").toAbsolutePath().normalize();
        }
        Files.createDirectories(directory);
        Path journalPath = directory.resolve(JOURNAL_FILE_NAME);
        Path activeBackup = Path.of(ABSENT_BACKUP);
        Path archiveBackup = Path.of(ABSENT_BACKUP);
        try {
            activeBackup = createBackup(directory, "active", activeSnapshot);
            archiveBackup = createBackup(directory, "archive", archiveSnapshot);
            StorageTransactionJournal journal = new StorageTransactionJournal(journalPath,
                    activeBackup, archiveBackup);
            journal.writePhase(activeFile, archiveFile, activeSnapshot, archiveSnapshot,
                    PREPARED_PHASE);
            return journal;
        } catch (IOException | RuntimeException e) {
            deleteOwnedFile(activeBackup);
            deleteOwnedFile(archiveBackup);
            deleteOwnedFile(journalPath);
            throw e;
        }
    }

    /** Records the phase after the active file has been replaced. */
    void markActiveCommitted(Path activeFile, Path archiveFile,
                             StorageFileManager.FileSnapshot activeSnapshot,
                             StorageFileManager.FileSnapshot archiveSnapshot) throws IOException {
        writePhase(activeFile, archiveFile, activeSnapshot, archiveSnapshot, ACTIVE_COMMITTED_PHASE);
    }

    /** Records the phase after both new files have been replaced. */
    void markCommitted(Path activeFile, Path archiveFile,
                       StorageFileManager.FileSnapshot activeSnapshot,
                       StorageFileManager.FileSnapshot archiveSnapshot) throws IOException {
        writePhase(activeFile, archiveFile, activeSnapshot, archiveSnapshot, COMMITTED_PHASE);
    }

    /** Removes a completed transaction's journal and backups. */
    void cleanUp() {
        deleteOwnedFile(activeBackup);
        deleteOwnedFile(archiveBackup);
        deleteOwnedFile(journalPath);
    }

    /** Returns the recovery journal location for a user-facing recovery message. */
    String getRecoveryLocation() {
        return journalPath.toAbsolutePath().normalize().toString();
    }

    /** Recovers an interrupted transaction belonging to the supplied data paths. */
    static void recoverPendingTransaction(Path activeFile, Path archiveFile) throws HertaException {
        try (StorageFileLock ignored = StorageFileLock.acquire(activeFile, archiveFile)) {
            recoverPendingTransactionWhileLocked(activeFile, archiveFile);
        } catch (HertaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Unable to inspect the Herta transaction journal.", e);
            throw new HertaException("Failed to recover interrupted storage transaction safely.");
        }
    }

    /** Performs journal recovery while cooperating writers are excluded. */
    private static void recoverPendingTransactionWhileLocked(Path activeFile, Path archiveFile)
            throws HertaException {
        Path journalPath = getJournalPath(activeFile);
        if (isJournalMissing(journalPath)) {
            cleanStaleTemporaryFiles(activeFile);
            return;
        }
        try {
            Properties properties = loadProperties(journalPath);
            validateOwnership(properties, activeFile, archiveFile);
            String phase = properties.getProperty(PHASE_KEY);
            if (COMMITTED_PHASE.equals(phase)) {
                deleteBackupFiles(properties);
            } else {
                restorePath(properties, ACTIVE_PATH_KEY, ACTIVE_PRESENT_KEY, ACTIVE_BACKUP_KEY);
                restorePath(properties, ARCHIVE_PATH_KEY, ARCHIVE_PRESENT_KEY, ARCHIVE_BACKUP_KEY);
                deleteBackupFiles(properties);
            }
            deleteOwnedFile(journalPath);
            cleanStaleTemporaryFiles(activeFile);
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Unable to validate the Herta transaction journal.", e);
            throw new HertaException("Failed to recover interrupted storage transaction safely.");
        }
    }

    /** Deletes only the validated backup paths from a journal. */
    private static void deleteBackupFiles(Properties properties) {
        deleteOwnedFile(Path.of(properties.getProperty(ACTIVE_BACKUP_KEY)));
        deleteOwnedFile(Path.of(properties.getProperty(ARCHIVE_BACKUP_KEY)));
    }

    /** Writes the journal and forces it so the recovery phase survives a power loss. */
    private void writePhase(Path activeFile, Path archiveFile,
                            StorageFileManager.FileSnapshot activeSnapshot,
                            StorageFileManager.FileSnapshot archiveSnapshot,
                            String phase) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(ACTIVE_PATH_KEY, normalize(activeFile).toString());
        properties.setProperty(ARCHIVE_PATH_KEY, normalize(archiveFile).toString());
        properties.setProperty(ACTIVE_PRESENT_KEY, Boolean.toString(activeSnapshot.wasPresent()));
        properties.setProperty(ARCHIVE_PRESENT_KEY, Boolean.toString(archiveSnapshot.wasPresent()));
        properties.setProperty(ACTIVE_BACKUP_KEY, activeBackup.toString());
        properties.setProperty(ARCHIVE_BACKUP_KEY, archiveBackup.toString());
        properties.setProperty(PHASE_KEY, phase);
        String serializedProperties = serializeProperties(properties);
        Path temporaryJournal = null;
        try {
            temporaryJournal = StorageFileManager.createOwnedTemporaryFile(
                    journalPath.getParent(), ".herta-transaction-", ".tmp");
            Files.writeString(temporaryJournal, serializedProperties, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            forcePath(temporaryJournal);
            replaceJournal(temporaryJournal);
            temporaryJournal = null;
        } finally {
            deleteOwnedFile(temporaryJournal);
        }
    }

    /** Replaces the journal atomically when the file system supports it. */
    private void replaceJournal(Path temporaryJournal) throws IOException {
        try {
            Files.move(temporaryJournal, journalPath, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException | AccessDeniedException e) {
            Files.move(temporaryJournal, journalPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Creates and forces one owned backup file, or records the absence of the original file. */
    private static Path createBackup(Path directory, String fileRole,
                                     StorageFileManager.FileSnapshot snapshot) throws IOException {
        if (!snapshot.wasPresent()) {
            return Path.of(ABSENT_BACKUP);
        }
        Path backup = StorageFileManager.createOwnedTemporaryFile(directory,
                ".herta-" + fileRole + "-", ".bak");
        Files.write(backup, snapshot.contents(), StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(backup, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        return backup;
    }

    /** Loads a journal without exposing its platform-specific contents to the caller. */
    private static Properties loadProperties(Path journalPath) throws IOException {
        validatePropertyKeys(journalPath);
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(journalPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (!properties.stringPropertyNames().equals(JOURNAL_KEYS)) {
            throw new IOException("Storage transaction journal schema is invalid.");
        }
        return properties;
    }

    /** Rejects duplicate or unknown property declarations before Properties resolves them. */
    private static void validatePropertyKeys(Path journalPath) throws IOException {
        Set<String> declaredKeys = new HashSet<>();
        try (var reader = Files.newBufferedReader(journalPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String key = extractPropertyKey(line);
                if (key.isEmpty()) {
                    continue;
                }
                if (!JOURNAL_KEYS.contains(key) || !declaredKeys.add(key)) {
                    throw new IOException("Storage transaction journal schema is invalid.");
                }
            }
        }
    }

    /** Returns the simple key in one generated journal property line. */
    private static String extractPropertyKey(String line) {
        String trimmedLine = line.trim();
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("!")) {
            return "";
        }
        int separatorIndex = trimmedLine.indexOf('=');
        if (separatorIndex < 0) {
            separatorIndex = trimmedLine.indexOf(':');
        }
        return separatorIndex < 0 ? trimmedLine : trimmedLine.substring(0, separatorIndex).trim();
    }

    /** Ensures a journal can only recover the files it was created to manage. */
    private static void validateOwnership(Properties properties, Path activeFile, Path archiveFile)
            throws IOException {
        Path normalizedActiveFile = normalize(activeFile);
        Path normalizedArchiveFile = normalize(archiveFile);
        validatePathProperty(properties, ACTIVE_PATH_KEY, normalizedActiveFile);
        validatePathProperty(properties, ARCHIVE_PATH_KEY, normalizedArchiveFile);
        boolean wasActivePresent = parseBooleanProperty(properties, ACTIVE_PRESENT_KEY);
        boolean wasArchivePresent = parseBooleanProperty(properties, ARCHIVE_PRESENT_KEY);
        validatePhase(properties.getProperty(PHASE_KEY));
        Path directory = normalizedActiveFile.getParent();
        validateBackupPath(properties.getProperty(ACTIVE_BACKUP_KEY), directory,
                ".herta-active-", wasActivePresent);
        validateBackupPath(properties.getProperty(ARCHIVE_BACKUP_KEY), directory,
                ".herta-archive-", wasArchivePresent);
    }

    /** Verifies that a required journal path is exactly the normalized owned path. */
    private static void validatePathProperty(Properties properties, String key, Path expectedPath)
            throws IOException {
        String pathText = properties.getProperty(key);
        if (pathText == null || !normalize(Path.of(pathText)).toString().equals(pathText)
                || !expectedPath.toString().equals(pathText)) {
            throw new IOException("Storage transaction ownership mismatch.");
        }
    }

    /** Verifies that a journal backup stays beside the data file and has Herta's prefix. */
    private static void validateBackupPath(String backupText, Path directory, String prefix,
                                           boolean wasPresent)
            throws IOException {
        if (!wasPresent && ABSENT_BACKUP.equals(backupText)) {
            return;
        }
        if (!wasPresent || backupText == null || ABSENT_BACKUP.equals(backupText)) {
            throw new IOException("Storage transaction backup is missing.");
        }
        Path backup = normalize(Path.of(backupText));
        String fileName = backup.getFileName().toString();
        boolean isBesideDataFile = Objects.equals(backup.getParent(), directory);
        boolean hasExpectedPrefix = fileName.startsWith(prefix);
        boolean hasExpectedSuffix = fileName.endsWith(".bak");
        boolean isNormalizedPath = backup.toString().equals(backupText);
        if (!isBesideDataFile || !hasExpectedPrefix || !hasExpectedSuffix || !isNormalizedPath) {
            throw new IOException("Storage transaction backup ownership mismatch.");
        }
        BasicFileAttributes attributes = Files.readAttributes(backup,
                BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() > StorageFileManager.MAX_STORAGE_FILE_BYTES) {
            throw new IOException("Storage transaction backup is not readable.");
        }
        try (var input = Files.newInputStream(backup)) {
            input.read();
        }
    }

    /** Parses a journal boolean without accepting missing or lookalike values. */
    private static boolean parseBooleanProperty(Properties properties, String key) throws IOException {
        String text = properties.getProperty(key);
        if ("true".equals(text)) {
            return true;
        }
        if ("false".equals(text)) {
            return false;
        }
        throw new IOException("Storage transaction journal boolean is invalid.");
    }

    /** Rejects phases that are not part of the journal state machine. */
    private static void validatePhase(String phase) throws IOException {
        if (!PREPARED_PHASE.equals(phase) && !ACTIVE_COMMITTED_PHASE.equals(phase)
                && !COMMITTED_PHASE.equals(phase)) {
            throw new IOException("Storage transaction journal phase is invalid.");
        }
    }

    /** Restores one snapshot from its owned backup or restores its recorded absence. */
    private static void restorePath(Properties properties, String pathKey, String presentKey,
                                    String backupKey) throws IOException {
        Path path = Path.of(properties.getProperty(pathKey));
        boolean wasPresent = parseBooleanProperty(properties, presentKey);
        String backupText = properties.getProperty(backupKey);
        boolean isAbsentBackup = ABSENT_BACKUP.equals(backupText);
        if (!wasPresent || isAbsentBackup) {
            Files.deleteIfExists(path);
            return;
        }
        Files.copy(Path.of(backupText), path, StandardCopyOption.REPLACE_EXISTING);
        forcePath(path);
    }

    /** Forces a restored data path so recovery is durable before startup continues. */
    private static void forcePath(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /** Deletes only a path supplied by the transaction journal. */
    private static void deleteOwnedFile(Path path) {
        if (path == null || ABSENT_BACKUP.equals(path.toString())) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Unable to clean up Herta transaction file: " + path, e);
        }
    }

    /** Removes old files using Herta's temporary-file prefix after ownership and age checks. */
    private static void cleanStaleTemporaryFiles(Path activeFile) {
        Path directory = activeFile.toAbsolutePath().normalize().getParent();
        if (directory == null) {
            return;
        }
        cleanStaleFiles(directory, ".herta-*.tmp");
        cleanStaleFiles(directory, ".herta-*.bak");
    }

    /** Removes old files that match an owned Herta temporary-file pattern. */
    private static void cleanStaleFiles(Path directory, String pattern) {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, pattern)) {
            Instant cutoff = Instant.now().minus(STALE_FILE_AGE);
            for (Path path : paths) {
                FileTime lastModified = Files.getLastModifiedTime(path);
                if (lastModified.toInstant().isBefore(cutoff)) {
                    deleteOwnedFile(path);
                }
            }
        } catch (IOException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Unable to inspect Herta temporary files.", e);
        }
    }

    /** Distinguishes a missing journal from one that cannot safely be inspected. */
    private static boolean isJournalMissing(Path journalPath) throws HertaException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(journalPath,
                    BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                throw new HertaException("Failed to recover interrupted storage transaction safely.");
            }
            return false;
        } catch (NoSuchFileException e) {
            return true;
        } catch (IOException | SecurityException e) {
            throw new HertaException("Failed to recover interrupted storage transaction safely.");
        }
    }

    /** Returns the journal beside the active file. */
    private static Path getJournalPath(Path activeFile) {
        Path directory = activeFile.toAbsolutePath().normalize().getParent();
        return (directory == null ? Path.of(".").toAbsolutePath() : directory)
                .resolve(JOURNAL_FILE_NAME);
    }

    /** Returns a normalized absolute path for journal ownership comparisons. */
    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /** Serializes the journal properties before they are forced to disk. */
    private static String serializeProperties(Properties properties) throws IOException {
        try (StringWriter output = new StringWriter()) {
            properties.store(output, "Herta transaction journal");
            return output.toString();
        }
    }
}
