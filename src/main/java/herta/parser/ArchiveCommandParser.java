package herta.parser;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import herta.exception.HertaException;

/**
 * Parses archive and restore task selections.
 */
final class ArchiveCommandParser {
    private static final String ARCHIVE_USAGE = "Use: archive <number> [<number> ...], "
            + "archive <start>-<end>, or archive all.";
    private static final String ARCHIVE_SELECTION_ERROR = "That's not a valid task selection. "
            + "Try: archive 1 3-5.";
    private static final String ARCHIVE_DESCENDING_RANGE_ERROR = "That range makes no sense. "
            + "Use an ascending range such as archive 2-5.";
    private static final String ARCHIVE_ALL_ERROR = "Use archive all by itself, or select task numbers and ranges.";
    private static final String RESTORE_USAGE = "Use: restore <archived task number>.";
    private static final String RESTORE_NUMBER_ERROR = "That's not an archived task number. "
            + "Try: restore 1.";
    private static final int MAX_NUMBER_LENGTH = 64;

    /**
     * Parses the selectors from an archive command.
     *
     * @param input the complete archive command
     * @return the validated archive selection
     * @throws HertaException if the selectors are malformed or out of integer range
     */
    ArchiveSelection parseArchiveSelection(String input) throws HertaException {
        String arguments = CommandType.ARCHIVE.extractArguments(input);
        if (arguments.isEmpty()) {
            throw new HertaException(ARCHIVE_USAGE);
        }

        String[] selectorInputs = arguments.split("\\s+");
        if (containsAllSelector(selectorInputs)) {
            return parseAllSelection(selectorInputs);
        }
        return new ArchiveSelection(parseArchiveRanges(selectorInputs), false);
    }

    /**
     * Checks whether an archive selection contains the special {@code all} selector.
     *
     * @param selectorInputs the individual archive selectors
     * @return {@code true} if one selector requests all completed tasks
     */
    private boolean containsAllSelector(String[] selectorInputs) {
        for (String selectorInput : selectorInputs) {
            if (selectorInput.equals("all")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the special selection that archives all completed tasks.
     *
     * @param selectorInputs the individual archive selectors
     * @return a selection for all completed tasks
     * @throws HertaException if {@code all} is combined with another selector
     */
    private ArchiveSelection parseAllSelection(String[] selectorInputs) throws HertaException {
        if (selectorInputs.length != 1) {
            throw new HertaException(ARCHIVE_ALL_ERROR);
        }
        return new ArchiveSelection(List.of(), true);
    }

    /**
     * Parses and validates numeric archive ranges.
     *
     * @param selectorInputs the individual numeric selectors
     * @return validated archive ranges
     * @throws HertaException if a selector is malformed or out of integer range
     */
    private List<ArchiveRange> parseArchiveRanges(String[] selectorInputs) throws HertaException {
        List<String[]> rawRanges = splitArchiveRanges(selectorInputs);
        validateAscendingRanges(rawRanges);
        return convertArchiveRanges(rawRanges);
    }

    /**
     * Splits numeric archive selectors into their endpoint strings.
     *
     * @param selectorInputs the individual numeric selectors
     * @return the raw range endpoints
     * @throws HertaException if a selector is malformed
     */
    private List<String[]> splitArchiveRanges(String[] selectorInputs) throws HertaException {
        List<String[]> rawRanges = new ArrayList<>();
        for (String selectorInput : selectorInputs) {
            if (!selectorInput.matches("[0-9]+(?:-[0-9]+)?")
                    || selectorInput.length() > MAX_NUMBER_LENGTH) {
                throw new HertaException(ARCHIVE_SELECTION_ERROR);
            }
            String[] endpoints = selectorInput.split("-", -1);
            if (containsZeroEndpoint(endpoints)) {
                throw new HertaException(ARCHIVE_SELECTION_ERROR);
            }
            rawRanges.add(endpoints);
        }
        return rawRanges;
    }

    /** Rejects zero because task numbers are one-based and strictly positive. */
    private boolean containsZeroEndpoint(String[] endpoints) {
        for (String endpoint : endpoints) {
            if (new BigInteger(endpoint).signum() == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rejects ranges whose end comes before their start.
     *
     * @param rawRanges the raw archive range endpoints
     * @throws HertaException if a range is descending
     */
    private void validateAscendingRanges(List<String[]> rawRanges) throws HertaException {
        for (String[] endpoints : rawRanges) {
            if (endpoints.length == 2
                    && new BigInteger(endpoints[0]).compareTo(new BigInteger(endpoints[1])) > 0) {
                throw new HertaException(ARCHIVE_DESCENDING_RANGE_ERROR);
            }
        }
    }

    /**
     * Converts raw archive range endpoints into validated integer ranges.
     *
     * @param rawRanges the raw archive range endpoints
     * @return converted archive ranges
     * @throws HertaException if an endpoint does not fit in an integer
     */
    private List<ArchiveRange> convertArchiveRanges(List<String[]> rawRanges) throws HertaException {
        List<ArchiveRange> ranges = new ArrayList<>();
        try {
            for (String[] endpoints : rawRanges) {
                BigInteger startValue = new BigInteger(endpoints[0]);
                BigInteger endValue = endpoints.length == 1
                        ? startValue : new BigInteger(endpoints[1]);
                ranges.add(new ArchiveRange(startValue.intValueExact(), endValue.intValueExact()));
            }
        } catch (ArithmeticException e) {
            throw new HertaException(ARCHIVE_SELECTION_ERROR);
        }
        return ranges;
    }

    /**
     * Parses the single archive task number from a restore command.
     *
     * @param input the complete restore command
     * @return the selected archive task number, converted to a zero-based index
     * @throws HertaException if the argument is missing or not a non-negative integer
     */
    int parseRestoreTaskNumber(String input) throws HertaException {
        String arguments = CommandType.RESTORE.extractArguments(input);
        if (arguments.isEmpty()) {
            throw new HertaException(RESTORE_USAGE);
        }
        if (!arguments.matches("[0-9]+") || arguments.length() > MAX_NUMBER_LENGTH) {
            throw new HertaException(RESTORE_NUMBER_ERROR);
        }

        try {
            BigInteger archiveNumber = new BigInteger(arguments);
            if (archiveNumber.signum() <= 0) {
                throw new HertaException(RESTORE_NUMBER_ERROR);
            }
            return archiveNumber.intValueExact() - 1;
        } catch (ArithmeticException e) {
            throw new HertaException(RESTORE_NUMBER_ERROR);
        }
    }

}
