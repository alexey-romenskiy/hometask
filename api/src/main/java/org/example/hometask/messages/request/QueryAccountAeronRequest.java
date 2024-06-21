package org.example.hometask.messages.request;

import org.jetbrains.annotations.NotNull;

public record QueryAccountAeronRequest(
        long trackingId,
        long accountId
) implements AeronRequest {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
