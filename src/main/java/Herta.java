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
import java.util.Scanner;

/**
 * Provides the command-line entry point for the Herta task manager.
 */
public class Herta {
    private static final String INDENT = "     ";
    private static final String SEPARATOR = "____________________________________________________________";
    private static final Path DATA_FILE = Path.of("data", "herta.txt");

    /**
     * Prints each line of chatbot output with the standard indentation.
     *
     * @param message the message to print.
     */
    private static void printIndented(String message) {
        // Split message wherever a line break occurs
        String[] lines = message.split("\\R");

        for (int i = 0; i < lines.length; i++) {
            System.out.println(INDENT + lines[i]);
        }
    }

    /**
     * Prints a task using the indentation used for task details.
     *
     * @param task the task to print.
     */
    private static void printTask(Task task) {
        printIndented("  " + task);
    }

    /**
     * Starts Herta and processes commands entered by the user.
     *
     * @param args command-line arguments, which are not used.
     */
    public static void main(String[] args) {
        String banner = " _   _           _\n"
                + "| | | | ___ _ __| |_ __ _\n"
                + "| |_| |/ _ \\ '__| __/ _` |\n"
                + "|  _  |  __/ |  | || (_| |\n"
                + "|_| |_|\\___|_|   \\__\\__,_|\n";
        printIndented(SEPARATOR);
        printIndented(banner);
        printIndented("Oh, you're here. I'm Herta.");
        printIndented("Well? What do you want?");
        printIndented(SEPARATOR);

        List<Task> tasks;
        try {
            tasks = loadTasks();
        } catch (HertaException e) {
            printIndented(e.getMessage());
            return;
        }

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Your command? ");
            if (!scanner.hasNextLine()) {
                printIndented(SEPARATOR);
                printIndented("Leaving already? Goodbye.");
                printIndented(SEPARATOR);
                break;
            }
            String input = scanner.nextLine().trim();
            printIndented(SEPARATOR);

            try {
                CommandType commandType = CommandType.fromInput(input);

                if (commandType == CommandType.BYE) {
                    printIndented("Leaving already? Goodbye.");
                    printIndented(SEPARATOR);
                    break;
                }

                switch (commandType) {
                    case LIST:
                        printIndented("Let's see what you've managed to pile up:");
                        for (int i = 0; i < tasks.size(); i++) {
                            printIndented((i + 1) + "." + tasks.get(i));
                        }
                        break;
                    case MARK:
                        markTask(tasks, input);
                        break;
                    case UNMARK:
                        unmarkTask(tasks, input);
                        break;
                    case DELETE:
                        deleteTask(tasks, input);
                        break;
                    case TODO:
                        addTodo(tasks, input);
                        break;
                    case DEADLINE:
                        addDeadline(tasks, input);
                        break;
                    case EVENT:
                        addEvent(tasks, input);
                        break;
                    case UNKNOWN:
                        if (input.isEmpty()) {
                            throw new HertaException("Nothing? Were you expecting me to read your mind?");
                        }
                        throw new HertaException("That command is invalid. Were you just guessing?\n" +
                                "Try todo, deadline, event, list, mark, unmark, delete, and bye.");
                    default:
                        break;
                    }
            } catch (HertaException e) {
                printIndented(e.getMessage());
            }

            printIndented(SEPARATOR);
        }

        scanner.close();
    }

    /**
     * Loads all saved tasks from the data file.
     *
     * <p>A missing data file represents a fresh start, so an empty task list
     * is returned in that case.</p>
     *
     * @return the tasks reconstructed from the saved lines.
     * @throws HertaException if the data file cannot be read or parsed.
     */
    private static List<Task> loadTasks() throws HertaException {
        List<Task> tasks = new ArrayList<>();

        try {
            if (Files.isDirectory(DATA_FILE)) {
                throw new HertaException("Failed to load tasks: data path is not a regular file.");
            }
            if (Files.notExists(DATA_FILE)) {
                return tasks;
            }
            if (!Files.isRegularFile(DATA_FILE)) {
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
     * Reads the data file as strict UTF-8 instead of silently replacing malformed bytes.
     *
     * @return the lines from the data file.
     * @throws IOException if the file cannot be read or is not valid UTF-8
     */
    private static List<String> readStorageLines() throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(DATA_FILE), decoder))) {
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
     * @param line one line in Herta's storage format.
     * @return the reconstructed task.
     * @throws HertaException if the line does not contain a supported task type.
     */
    private static Task parseTask(String line) throws HertaException {
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

        Task task = switch (type) {
            case "T" -> new Todo(parts[2]);
            case "D" -> new Deadline(parts[2], parts[3]);
            case "E" -> new Event(parts[2], parts[3], parts[4]);
            default -> throw new HertaException("Invalid saved task: unknown task type '"
                    + type + "'.");
        };

        if (status == 1) {
            task.markAsDone();
        }
        return task;
    }

    /**
     * Adds a task and prints the standard confirmation message.
     *
     * @param tasks the task list to update.
     * @param task the task to add.
     */
    private static void addTask(List<Task> tasks, Task task) throws HertaException {
        List<Task> updatedTasks = new ArrayList<>(tasks);
        updatedTasks.add(task);
        saveTasks(updatedTasks);
        tasks.add(task);
        printIndented("There. I've added it:");
        printTask(task);
        printTaskCount(tasks);
    }

    /**
     * Saves the complete in-memory task list to the data file.
     *
     * <p>The file is rewritten instead of appended to so that deletions and
     * completion-status changes are reflected in the saved data.</p>
     *
     * @param tasks the task list to save.
     * @throws HertaException if the task list cannot be written.
     */
    private static void saveTasks(List<Task> tasks) throws HertaException {
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
            Path dataDirectory = DATA_FILE.getParent();
            Files.createDirectories(dataDirectory);
            if (Files.isDirectory(DATA_FILE)
                    || (Files.exists(DATA_FILE) && !Files.isRegularFile(DATA_FILE))) {
                throw new IOException("data path is not a regular file");
            }

            temporaryFile = Files.createTempFile(dataDirectory, ".herta-", ".tmp");
            Files.write(temporaryFile, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            try {
                Files.move(temporaryFile, DATA_FILE,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
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
     * Prints the number of tasks using the correct singular or plural noun.
     *
     * @param tasks the task list to count.
     */
    private static void printTaskCount(List<Task> tasks) {
        String taskNoun = tasks.size() == 1 ? "task" : "tasks";
        printIndented("That makes " + tasks.size() + " " + taskNoun
                + ". Try to keep up.");
    }

    /**
     * Parses and adds a todo command's description.
     *
     * @param tasks the task list to update.
     * @param input the complete todo command entered by the user.
     * @throws HertaException if the todo description is empty
     */
    private static void addTodo(List<Task> tasks, String input) throws HertaException {
        String description = input.substring("todo".length()).trim();
        if (description.isEmpty()) {
            throw new HertaException("A blank todo? Even I can't organise nothing. Use: todo <description>.");
        }
        addTask(tasks, new Todo(description));
    }

    /**
     * Parses and adds a deadline command's description and date/time.
     *
     * @param tasks the task list to update.
     * @param input the complete deadline command entered by the user.
     * @throws HertaException if the deadline format or required fields are invalid
     */
    private static void addDeadline(List<Task> tasks, String input) throws HertaException {
        String content = input.substring("deadline".length()).trim();
        String[] deadlineParts = content.split("\\s+/by\\s+", 2);
        if (deadlineParts.length != 2) {
            throw new HertaException("Did you even read the deadline format? "
                    + "Use: deadline <description> /by <date/time>.");
        }

        String description = deadlineParts[0].trim();
        String by = deadlineParts[1].trim();
        if (description.isEmpty() || by.isEmpty()) {
            throw new HertaException("Did you even read the deadline format? "
                    + "Use: deadline <description> /by <date/time>.");
        }

        addTask(tasks, new Deadline(description, by));
    }

    /**
     * Parses and adds an event command's description, start, and end date/time.
     *
     * @param tasks the task list to update.
     * @param input the complete event command entered by the user.
     * @throws HertaException if the event format or required fields are invalid
     */
    private static void addEvent(List<Task> tasks, String input) throws HertaException {
        String content = input.substring("event".length()).trim();
        String[] eventParts = content.split("\\s+/from\\s+", 2);
        if (eventParts.length != 2) {
            throw new HertaException("Did you even read the event format? "
                    + "Use: event <description> /from <start> /to <end>.");
        }

        String[] timeParts = eventParts[1].split("\\s+/to\\s+", 2);
        if (timeParts.length != 2) {
            throw new HertaException("Did you even read the event format? "
                    + "Use: event <description> /from <start> /to <end>.");
        }

        String description = eventParts[0].trim();
        String from = timeParts[0].trim();
        String to = timeParts[1].trim();
        if (description.isEmpty() || from.isEmpty() || to.isEmpty()) {
            throw new HertaException("Did you even read the event format? "
                    + "Use: event <description> /from <start> /to <end>.");
        }

        addTask(tasks, new Event(description, from, to));
    }

    /**
     * Deletes the requested task and prints the standard confirmation message.
     *
     * @param tasks the task list to update.
     * @param input the complete delete command entered by the user.
     * @throws HertaException if the task number is missing, invalid, or out of range
     */
    private static void deleteTask(List<Task> tasks, String input) throws HertaException {
        String taskNumber = input.substring("delete".length()).trim();
        Task task = getTask(tasks, taskNumber, "delete");
        List<Task> updatedTasks = new ArrayList<>(tasks);
        updatedTasks.remove(task);
        saveTasks(updatedTasks);
        tasks.remove(task);
        printIndented("There. It's gone:");
        printTask(task);
        printTaskCount(tasks);
    }

    /**
     * Marks the requested task as done.
     *
     * @param tasks the task list to update.
     * @param input the complete mark command entered by the user.
     * @throws HertaException if the task number is missing, invalid, or out of range
     */
    private static void markTask(List<Task> tasks, String input) throws HertaException {
        String taskNumber = input.substring("mark".length()).trim();
        Task task = getTask(tasks, taskNumber, "mark");
        boolean wasDone = task.isDone();
        task.markAsDone();
        try {
            saveTasks(tasks);
        } catch (HertaException e) {
            restoreTaskStatus(task, wasDone);
            throw e;
        }
        printIndented("There. It's marked complete:");
        printTask(task);
    }

    /**
     * Marks the requested task as not done.
     *
     * @param tasks the task list to update.
     * @param input the complete unmark command entered by the user.
     * @throws HertaException if the task number is missing, invalid, or out of range
     */
    private static void unmarkTask(List<Task> tasks, String input) throws HertaException {
        String taskNumber = input.substring("unmark".length()).trim();
        Task task = getTask(tasks, taskNumber, "unmark");
        boolean wasDone = task.isDone();
        task.markAsNotDone();
        try {
            saveTasks(tasks);
        } catch (HertaException e) {
            restoreTaskStatus(task, wasDone);
            throw e;
        }
        printIndented("As you wish. It's incomplete again:");
        printTask(task);
    }

    /**
     * Restores a task's completion status after a failed save.
     *
     * @param task the task whose status should be restored.
     * @param wasDone the status before the attempted update.
     */
    private static void restoreTaskStatus(Task task, boolean wasDone) {
        if (wasDone) {
            task.markAsDone();
        } else {
            task.markAsNotDone();
        }
    }

    /**
     * Finds a task using the one-based number shown by the list command.
     *
     * @param tasks the task list to search.
     * @param taskNumber the task number entered by the user.
     * @param command the command used to request the task.
     * @return the requested task
     * @throws HertaException if the task number is invalid or out of range
     */
    private static Task getTask(List<Task> tasks, String taskNumber, String command) throws HertaException {
        final int taskIndex;
        try {
            taskIndex = Integer.parseInt(taskNumber) - 1;
        } catch (NumberFormatException e) {
            throw new HertaException("That's not a task number. Try: " + command + " 1.");
        }

        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            throw new HertaException("That task doesn't exist. Did you even check the list?");
        }
        return tasks.get(taskIndex);
    }
}
