package herta.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import herta.exception.HertaException;
import herta.parser.ArchiveRange;
import herta.parser.ArchiveSelection;
import herta.storage.Storage;
import herta.storage.TaskRepository;
import herta.task.Task;
import herta.task.TaskList;
import herta.ui.UiOutput;

/**
 * Represents the command that moves completed active tasks to the archive.
 */
public class ArchiveCommand extends Command {
    private static final String ARCHIVE_FAILURE_PREFIX = "Failed to archive tasks: ";
    private final ArchiveSelection selection;

    /**
     * Creates an archive command for a validated selection.
     *
     * @param selection the task numbers or ranges to archive
     */
    public ArchiveCommand(ArchiveSelection selection) {
        this.selection = selection;
    }

    /**
     * Executes the command using an active and archive repository.
     *
     * @param repository the repository containing both task collections
     * @param ui the output interface used to display responses
     * @throws HertaException if selection validation fails or persistence fails
     */
    @Override
    public void execute(TaskRepository repository, UiOutput ui) throws HertaException {
        TaskList activeTasks = repository.getActiveTasks();
        Set<Integer> selectedIndices = getSelectedIndices(activeTasks);
        if (selection.isAllSelected() && activeTasks.size() == 0) {
            ui.showMessage("Nothing to archive. The active task list is already empty.");
            return;
        }
        if (selection.isAllSelected() && selectedIndices.isEmpty()) {
            ui.showMessage("Nothing to archive. There are no completed active tasks.");
            return;
        }

        if (!selection.isAllSelected()) {
            for (int index : selectedIndices) {
                if (!activeTasks.get(index).isCompleted()) {
                    throw new HertaException(
                            "Only completed tasks can be archived. Mark the task complete first.");
                }
            }
        }

        TaskList updatedActiveTasks = new TaskList();
        TaskList updatedArchivedTasks = new TaskList(repository.getArchivedTasks().asUnmodifiableList());
        List<Task> archivedForDisplay = new ArrayList<>();
        for (int index = 0; index < activeTasks.size(); index++) {
            Task task = activeTasks.get(index);
            if (selectedIndices.contains(index)) {
                updatedArchivedTasks.add(task);
                archivedForDisplay.add(task);
            } else {
                updatedActiveTasks.add(task);
            }
        }

        repository.saveBoth(updatedActiveTasks, updatedArchivedTasks, ARCHIVE_FAILURE_PREFIX);
        repository.replaceCollections(updatedActiveTasks, updatedArchivedTasks);
        ui.showMessage("There. I've archived " + selectedIndices.size()
                + " completed " + getTaskNoun(selectedIndices.size()) + ":");
        for (Task task : archivedForDisplay) {
            ui.showTask(task);
        }
        ui.showMessage("That leaves " + updatedActiveTasks.size() + " active "
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
     * Resolves selectors into unique zero-based active-list indices.
     *
     * @param activeTasks the active tasks against which bounds are checked
     * @return selected indices in active-list order
     * @throws HertaException if any selected task number is out of bounds
     */
    private Set<Integer> getSelectedIndices(TaskList activeTasks) throws HertaException {
        Set<Integer> selectedIndices = new TreeSet<>();
        if (selection.isAllSelected()) {
            selectedIndices.addAll(activeTasks.matchingIndices(Task::isCompleted));
            return selectedIndices;
        }

        for (ArchiveRange range : selection.getRanges()) {
            if (range.start() <= 0 || range.end() > activeTasks.size()) {
                throw new HertaException("That task doesn't exist. Did you even check the list?");
            }
        }
        for (ArchiveRange range : selection.getRanges()) {
            for (int taskNumber = range.start(); ; taskNumber++) {
                selectedIndices.add(taskNumber - 1);
                if (taskNumber == range.end()) {
                    break;
                }
            }
        }
        return selectedIndices;
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
