package herta.command;

import herta.exception.HertaException;
import herta.storage.Storage;
import herta.storage.TaskRepository;
import herta.task.Task;
import herta.task.TaskList;
import herta.ui.UiOutput;

/**
 * Represents the command that restores one archived task to the active list.
 */
public class RestoreCommand extends Command {
    private static final String RESTORE_FAILURE_PREFIX = "Failed to restore task: ";
    private final int archiveIndex;

    /**
     * Creates a restore command for a zero-based archive index.
     *
     * @param archiveIndex the zero-based index of the archived task
     */
    public RestoreCommand(int archiveIndex) {
        this.archiveIndex = archiveIndex;
    }

    /**
     * Restores the selected task and appends it to the active collection.
     *
     * @param repository the repository containing both task collections
     * @param ui the output interface used to display responses
     * @throws HertaException if the archive index is invalid or persistence fails
     */
    @Override
    public void execute(TaskRepository repository, UiOutput ui) throws HertaException {
        TaskList archivedTasks = repository.getArchivedTasks();
        if (archiveIndex < 0 || archiveIndex >= archivedTasks.size()) {
            throw new HertaException(
                    "That archived task doesn't exist. Did you even check the archived list?");
        }

        Task task = archivedTasks.get(archiveIndex);
        TaskList updatedActiveTasks = new TaskList(repository.getActiveTasks().asUnmodifiableList());
        TaskList updatedArchivedTasks = new TaskList(archivedTasks.asUnmodifiableList());
        updatedArchivedTasks.remove(archiveIndex);
        updatedActiveTasks.add(task);

        repository.saveBoth(updatedActiveTasks, updatedArchivedTasks, RESTORE_FAILURE_PREFIX);
        repository.replaceCollections(updatedActiveTasks, updatedArchivedTasks);
        ui.showMessage("There. I've restored it:");
        ui.showTask(task);
        ui.showMessage("That makes " + updatedActiveTasks.size() + " active "
                + getTaskNoun(updatedActiveTasks.size()) + ". Try to keep up.");
    }

    /**
     * Adapts legacy direct command execution to the two-collection repository.
     *
     * @param tasks the active tasks
     * @param ui the output interface used to display responses
     * @param storage storage for active tasks
     * @throws HertaException if the archive cannot be loaded or saved
     */
    @Override
    public void execute(TaskList tasks, UiOutput ui, Storage storage) throws HertaException {
        execute(TaskRepository.withActiveTasks(tasks, storage), ui);
    }

    /**
     * Returns the singular or plural noun for a task count.
     *
     * @param count the task count
     * @return {@code task} only for one, otherwise {@code tasks}
     */
    private String getTaskNoun(int count) {
        return count == 1 ? "task" : "tasks";
    }
}
