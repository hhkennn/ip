import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Owns Herta's ordered collection of tasks and its task-related operations.
 */
public class TaskList implements Iterable<Task> {
    private final List<Task> tasks;

    /**
     * Creates an empty task list.
     */
    public TaskList() {
        tasks = new ArrayList<>();
    }

    /**
     * Creates a task list containing a copy of the supplied tasks.
     *
     * @param initialTasks the tasks with which to initialise the list
     */
    public TaskList(List<Task> initialTasks) {
        tasks = new ArrayList<>(initialTasks);
    }

    /**
     * Returns the number of tasks in this list.
     *
     * @return the number of tasks
     */
    public int size() {
        return tasks.size();
    }

    /**
     * Returns the task at a zero-based index.
     *
     * @param index the zero-based task index
     * @return the task at the requested index
     */
    public Task get(int index) {
        return tasks.get(index);
    }

    /**
     * Adds a task to the end of this list.
     *
     * @param task the task to add
     */
    public void add(Task task) {
        tasks.add(task);
    }

    /**
     * Removes and returns the task at a zero-based index.
     *
     * @param index the zero-based task index
     * @return the removed task
     */
    public Task remove(int index) {
        return tasks.remove(index);
    }

    /**
     * Marks a task as complete and returns its previous status.
     *
     * @param index the zero-based task index
     * @return {@code true} if the task was already complete
     */
    public boolean markTask(int index) {
        Task task = get(index);
        boolean wasDone = task.isDone();
        task.markAsDone();
        return wasDone;
    }

    /**
     * Marks a task as incomplete and returns its previous status.
     *
     * @param index the zero-based task index
     * @return {@code true} if the task was complete before this operation
     */
    public boolean unmarkTask(int index) {
        Task task = get(index);
        boolean wasDone = task.isDone();
        task.markAsNotDone();
        return wasDone;
    }

    /**
     * Restores a task's completion status after a failed persistence attempt.
     *
     * @param index the zero-based task index
     * @param wasDone the status to restore
     */
    public void restoreStatus(int index, boolean wasDone) {
        if (wasDone) {
            get(index).markAsDone();
        } else {
            get(index).markAsNotDone();
        }
    }

    /**
     * Returns the indices of tasks that satisfy a condition.
     *
     * @param matcher the condition a task must satisfy
     * @return matching zero-based task indices in their current list order
     */
    public List<Integer> matchingIndices(Predicate<Task> matcher) {
        List<Integer> matchingIndices = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            if (matcher.test(tasks.get(i))) {
                matchingIndices.add(i);
            }
        }
        return matchingIndices;
    }

    /**
     * Returns task indices sorted by the supplied task comparator without
     * changing the stored order.
     *
     * @param comparator the comparator used to compare tasks
     * @return zero-based task indices in sorted order
     */
    public List<Integer> sortedIndices(Comparator<Task> comparator) {
        List<Integer> sortedIndices = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            sortedIndices.add(i);
        }
        sortedIndices.sort((first, second) -> comparator.compare(
                tasks.get(first), tasks.get(second)));
        return sortedIndices;
    }

    /**
     * Returns a read-only view of the tasks in their current order.
     *
     * @return an unmodifiable task-list view
     */
    public List<Task> asUnmodifiableList() {
        return Collections.unmodifiableList(tasks);
    }

    /**
     * Returns a read-only iterator over the tasks in their current order.
     *
     * @return an unmodifiable task iterator
     */
    @Override
    public Iterator<Task> iterator() {
        return asUnmodifiableList().iterator();
    }
}
