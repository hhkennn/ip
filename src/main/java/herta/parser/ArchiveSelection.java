package herta.parser;

import java.util.List;

/**
 * Represents the validated selectors supplied to an archive command.
 */
public final class ArchiveSelection {
    private final List<ArchiveRange> ranges;
    private final boolean all;

    /**
     * Creates an archive selection.
     *
     * @param ranges the inclusive, one-based ranges selected by the user
     * @param all whether the selection targets every completed active task
     */
    public ArchiveSelection(List<ArchiveRange> ranges, boolean all) {
        this.ranges = List.copyOf(ranges);
        this.all = all;
    }

    /**
     * Indicates whether this selection represents {@code archive all}.
     *
     * @return {@code true} when every completed active task is selected
     */
    public boolean isAll() {
        return all;
    }

    /**
     * Returns the selected task-number ranges.
     *
     * @return an immutable list of selected ranges
     */
    public List<ArchiveRange> getRanges() {
        return ranges;
    }
}
