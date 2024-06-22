package org.example.hometask.messages.disruptor;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record QueryWithdrawalUnknownIdFailureEvent(
        @NotNull UUID withdrawalUuid
) implements Event {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
