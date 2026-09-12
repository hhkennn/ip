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

    /** Stores the collections and display items produced by an archive operation. */
    private record ArchiveResult(TaskList activeTasks, TaskList archivedTasks,
                                 List<Task> archivedTasksForDisplay) {
    }

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
        if (showNoTasksMessage(activeTasks, selectedIndices, ui)) {
            return;
        }

        validateCompletedTasks(activeTasks, selectedIndices);
        ArchiveResult result = archiveSelectedTasks(activeTasks, repository.getArchivedTasks(),
                selectedIndices);
        repository.saveBoth(result.activeTasks(), result.archivedTasks(), ARCHIVE_FAILURE_PREFIX);
        repository.replaceCollections(result.activeTasks(), result.archivedTasks());
        showArchiveResult(result, selectedIndices.size(), ui);
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
     * Displays a no-op explanation for an all-task selection that has nothing to archive.
     *
     * @param activeTasks the active task collection
     * @param selectedIndices the resolved task indices
     * @param ui the output interface
     * @return {@code true} when the command should stop without archiving
     */
    private boolean showNoTasksMessage(TaskList activeTasks, Set<Integer> selectedIndices, UiOutput ui) {
        if (!selection.isAllSelected()) {
            return false;
        }
        if (activeTasks.size() == 0) {
            ui.showMessage("Nothing to archive. The active task list is already empty.");
            return true;
        }
        if (selectedIndices.isEmpty()) {
            ui.showMessage("Nothing to archive. There are no completed active tasks.");
            return true;
        }
        return false;
    }

    /**
     * Ensures every explicitly selected task is complete before archiving.
     *
     * @param activeTasks the active task collection
     * @param selectedIndices the resolved task indices
     * @throws HertaException if an explicitly selected task is incomplete
     */
    private void validateCompletedTasks(TaskList activeTasks, Set<Integer> selectedIndices)
            throws HertaException {
        if (selection.isAllSelected()) {
            return;
        }
        for (int index : selectedIndices) {
            if (!activeTasks.get(index).isCompleted()) {
                throw new HertaException(
                        "Only completed tasks can be archived. Mark the task complete first.");
            }
        }
    }

    /**
     * Moves selected tasks into new active and archived collections.
     *
     * @param activeTasks the active task collection
     * @param archivedTasks the existing archived task collection
     * @param selectedIndices the resolved task indices
     * @return the collections and tasks to display after archiving
     */
    private ArchiveResult archiveSelectedTasks(TaskList activeTasks, TaskList archivedTasks,
                                               Set<Integer> selectedIndices) {
        TaskList updatedActiveTasks = new TaskList();
        TaskList updatedArchivedTasks = new TaskList(archivedTasks.asUnmodifiableList());
        List<Task> archivedTasksForDisplay = new ArrayList<>();
        for (int index = 0; index < activeTasks.size(); index++) {
            Task task = activeTasks.get(index);
            if (selectedIndices.contains(index)) {
                updatedArchivedTasks.add(task);
                archivedTasksForDisplay.add(task);
            } else {
                updatedActiveTasks.add(task);
            }
        }
        return new ArchiveResult(updatedActiveTasks, updatedArchivedTasks, archivedTasksForDisplay);
    }

    /**
     * Displays the result of a successful archive operation.
     *
     * @param result the collections and tasks produced by the operation
     * @param archivedCount the number of tasks moved
     * @param ui the output interface
     */
    private void showArchiveResult(ArchiveResult result, int archivedCount, UiOutput ui) {
        ui.showMessage("There. I've archived " + archivedCount
                + " completed " + getTaskNoun(archivedCount) + ":");
        for (Task task : result.archivedTasksForDisplay()) {
            ui.showTask(task);
        }
        ui.showMessage("That leaves " + result.activeTasks().size() + " active "
                + getTaskNoun(result.activeTasks().size()) + ". Try to keep up.");
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
