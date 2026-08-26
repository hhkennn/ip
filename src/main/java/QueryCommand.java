import java.util.List;
import java.util.function.Predicate;

/**
 * Base class for commands that display tasks selected by a condition.
 */
public abstract class QueryCommand extends Command {

    /**
     * Displays the matching tasks and an explanatory message when there are no matches.
     *
     * @param tasks the task list to search
     * @param matcher the condition a task must satisfy
     * @param heading the heading to print before the results
     * @param emptyMessage the message to print when there are no matches
     * @param ui the user interface used for output
     */
    protected void showMatchingTasks(TaskList tasks, Predicate<Task> matcher,
                                     String heading, String emptyMessage, Ui ui) {
        ui.showMessage(heading);
        List<Integer> matchingIndices = tasks.matchingIndices(matcher);
        for (int index : matchingIndices) {
            ui.showMessage((index + 1) + "." + tasks.get(index));
        }
        if (matchingIndices.isEmpty()) {
            ui.showMessage(emptyMessage);
        }
    }
}
