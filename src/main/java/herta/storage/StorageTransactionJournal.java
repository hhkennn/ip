package herta.storage;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Properties;
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
        Path journalPath = getJournalPath(activeFile);
        if (isJournalMissing(journalPath)) {
            cleanStaleTemporaryFiles(activeFile);
            return;
        }
        try {
            Properties properties = loadProperties(journalPath);
            validateOwnership(properties, activeFile, archiveFile);
            if (COMMITTED_PHASE.equals(properties.getProperty(PHASE_KEY))) {
                deleteOwnedFile(Path.of(properties.getProperty(ACTIVE_BACKUP_KEY)));
                deleteOwnedFile(Path.of(properties.getProperty(ARCHIVE_BACKUP_KEY)));
                deleteOwnedFile(journalPath);
            } else {
                restorePath(properties, ACTIVE_PATH_KEY, ACTIVE_PRESENT_KEY, ACTIVE_BACKUP_KEY);
                restorePath(properties, ARCHIVE_PATH_KEY, ARCHIVE_PRESENT_KEY, ARCHIVE_BACKUP_KEY);
                deleteOwnedFile(Path.of(properties.getProperty(ACTIVE_BACKUP_KEY)));
                deleteOwnedFile(Path.of(properties.getProperty(ARCHIVE_BACKUP_KEY)));
                deleteOwnedFile(journalPath);
            }
            cleanStaleTemporaryFiles(activeFile);
        } catch (IOException | RuntimeException e) {
            throw new HertaException("Failed to recover interrupted storage transaction safely.");
        }
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
        Files.writeString(journalPath, serializedProperties, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(journalPath, StandardOpenOption.WRITE)) {
            channel.force(true);
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
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(journalPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    /** Ensures a journal can only recover the files it was created to manage. */
    private static void validateOwnership(Properties properties, Path activeFile, Path archiveFile)
            throws IOException {
        Path normalizedActiveFile = normalize(activeFile);
        Path normalizedArchiveFile = normalize(archiveFile);
        boolean hasActiveOwnership = normalizedActiveFile.toString()
                .equals(properties.getProperty(ACTIVE_PATH_KEY));
        boolean hasArchiveOwnership = normalizedArchiveFile.toString()
                .equals(properties.getProperty(ARCHIVE_PATH_KEY));
        if (!hasActiveOwnership || !hasArchiveOwnership) {
            throw new IOException("Storage transaction ownership mismatch.");
        }
        Path directory = normalizedActiveFile.getParent();
        validateBackupPath(properties.getProperty(ACTIVE_BACKUP_KEY), directory,
                ".herta-active-");
        validateBackupPath(properties.getProperty(ARCHIVE_BACKUP_KEY), directory,
                ".herta-archive-");
    }

    /** Verifies that a journal backup stays beside the data file and has Herta's prefix. */
    private static void validateBackupPath(String backupText, Path directory, String prefix)
            throws IOException {
        if (ABSENT_BACKUP.equals(backupText)) {
            return;
        }
        if (backupText == null) {
            throw new IOException("Storage transaction backup is missing.");
        }
        Path backup = normalize(Path.of(backupText));
        String fileName = backup.getFileName().toString();
        boolean isBesideDataFile = Objects.equals(backup.getParent(), directory);
        boolean hasExpectedPrefix = fileName.startsWith(prefix);
        boolean hasExpectedSuffix = fileName.endsWith(".bak");
        if (!isBesideDataFile || !hasExpectedPrefix || !hasExpectedSuffix) {
            throw new IOException("Storage transaction backup ownership mismatch.");
        }
    }

    /** Restores one snapshot from its owned backup or restores its recorded absence. */
    private static void restorePath(Properties properties, String pathKey, String presentKey,
                                    String backupKey) throws IOException {
        Path path = Path.of(properties.getProperty(pathKey));
        boolean wasPresent = Boolean.parseBoolean(properties.getProperty(presentKey));
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
