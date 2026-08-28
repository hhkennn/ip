package herta.command;

import java.util.Locale;

import herta.storage.Storage;
import herta.task.TaskList;
import herta.ui.Ui;

/**
 * Represents the command that searches task descriptions for a keyword.
 */
public class FindCommand extends QueryCommand {
    private final String keyword;
    private final String normalizedKeyword;

    /**
     * Creates a command that searches task descriptions for the given keyword.
     *
     * @param keyword the text to find in task descriptions
     */
    public FindCommand(String keyword) {
        this.keyword = keyword;
        normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
    }

    /**
     * Displays tasks whose descriptions contain the search keyword.
     *
     * @param tasks the task list to search
     * @param ui the user interface used for output
     * @param storage unused because searching does not change stored data
     */
    @Override
    public void execute(TaskList tasks, Ui ui, Storage storage) {
        showMatchingTasks(tasks,
                task -> task.getDescription().toLowerCase(Locale.ROOT)
                        .contains(normalizedKeyword),
                "Here are the matching tasks in your list:",
                "No tasks match the keyword: " + keyword,
                ui);
    }
}
