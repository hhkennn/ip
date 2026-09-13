package herta.task;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable identity used to detect duplicate tasks without comparing mutable status.
 *
 * @param taskType the concrete task type
 * @param normalizedDescription the duplicate-normalized description
 * @param firstDateTime the first temporal field, if any
 * @param secondDateTime the second temporal field, if any
 */
public record TaskIdentity(Class<? extends Task> taskType, String normalizedDescription,
                           LocalDateTime firstDateTime, LocalDateTime secondDateTime) {
    /** Creates a task identity after checking all identity fields. */
    public TaskIdentity {
        Objects.requireNonNull(taskType, "A task identity needs a task type.");
        Objects.requireNonNull(normalizedDescription,
                "A task identity needs a normalized description.");
    }
}
