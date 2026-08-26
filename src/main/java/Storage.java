import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles loading tasks from and saving tasks to Herta's data file.
 */
public class Storage {
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
     * Loads all saved tasks from the data file.
     *
     * <p>A missing data file represents a fresh start, so an empty task list
     * is returned in that case.</p>
     *
     * @return the tasks reconstructed from the saved lines
     * @throws HertaException if the data file cannot be read or parsed
     */
    public List<Task> load() throws HertaException {
        List<Task> tasks = new ArrayList<>();

        try {
            if (Files.isDirectory(dataFile)) {
                throw new HertaException("Failed to load tasks: data path is not a regular file.");
            }
            if (Files.notExists(dataFile)) {
                return tasks;
            }
            if (!Files.isRegularFile(dataFile)) {
                throw new HertaException("Failed to load tasks: data path is not a regular file.");
            }

            List<String> lines = readStorageLines();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.isBlank()) {
                    try {
                        tasks.add(parseTask(line));
                    } catch (HertaException e) {
                        throw new HertaException("Failed to load tasks at line "
                                + (i + 1) + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException | SecurityException e) {
            throw new HertaException("Failed to load tasks: " + e.getMessage());
        }

        return tasks;
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
    public void save(List<Task> tasks) throws HertaException {
        if (tasks == null) {
            throw new HertaException("Failed to save tasks: task list is null.");
        }

        List<String> lines = new ArrayList<>();
        for (Task task : tasks) {
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
                parseTask(storageString);
            } catch (HertaException e) {
                throw new HertaException("Failed to save tasks: " + e.getMessage());
            }
            lines.add(storageString);
        }

        Path temporaryFile = null;
        try {
            Path dataDirectory = dataFile.getParent();
            if (dataDirectory == null) {
                dataDirectory = Path.of(".");
            }
            Files.createDirectories(dataDirectory);
            if (Files.isDirectory(dataFile)
                    || (Files.exists(dataFile) && !Files.isRegularFile(dataFile))) {
                throw new IOException("data path is not a regular file");
            }

            temporaryFile = Files.createTempFile(dataDirectory, ".herta-", ".tmp");
            Files.write(temporaryFile, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            try {
                Files.move(temporaryFile, dataFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException e) {
            throw new HertaException("Failed to save tasks: " + e.getMessage());
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException | SecurityException ignored) {
                    // The original data file is still preserved if cleanup fails.
                }
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
     * Reconstructs a task from one serialized data-file line.
     *
     * @param line one line in Herta's storage format
     * @return the reconstructed task
     * @throws HertaException if the line does not contain a supported task type
     */
    private Task parseTask(String line) throws HertaException {
        String normalizedLine = line.trim();
        if (normalizedLine.startsWith("\uFEFF")) {
            normalizedLine = normalizedLine.substring(1).trim();
        }
        String[] parts = normalizedLine.split("\\s*\\|\\s*", -1);
        if (parts.length < 2) {
            throw new HertaException("Invalid saved task: missing task type or status.");
        }

        String type = parts[0];
        int expectedParts = switch (type) {
            case "T" -> 3;
            case "D" -> 4;
            case "E" -> 5;
            default -> throw new HertaException("Invalid saved task: unknown task type '"
                    + type + "'.");
        };

        if (parts.length != expectedParts) {
            throw new HertaException("Invalid saved task: type " + type
                    + " requires " + expectedParts + " fields.");
        }

        if (!parts[1].equals("0") && !parts[1].equals("1")) {
            throw new HertaException("Invalid saved task: completion status must be 0 or 1.");
        }
        int status = Integer.parseInt(parts[1]);

        for (int i = 2; i < parts.length; i++) {
            if (parts[i].isBlank()) {
                throw new HertaException("Invalid saved task: task fields cannot be blank.");
            }
        }

        final Task task;
        try {
            task = switch (type) {
                case "T" -> new Todo(parts[2]);
                case "D" -> Deadline.fromStorage(parts[2], parts[3]);
                case "E" -> Event.fromStorage(parts[2], parts[3], parts[4]);
                default -> throw new HertaException("Invalid saved task: unknown task type '"
                        + type + "'.");
            };
        } catch (IllegalArgumentException e) {
            throw new HertaException(e.getMessage());
        }

        if (status == 1) {
            task.markAsDone();
        }
        return task;
    }
}
