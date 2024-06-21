package org.example.hometask.messages.disruptor;

import org.example.hometask.messages.request.AeronRequest;
import org.jetbrains.annotations.NotNull;

public record InboundAeronMessageEvent(
        int sessionId,
        @NotNull AeronRequest message
) implements Event {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
