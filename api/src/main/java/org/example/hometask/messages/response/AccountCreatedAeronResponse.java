package org.example.hometask.messages.response;

import org.jetbrains.annotations.NotNull;

public record AccountCreatedAeronResponse(
        long trackingId,
        long accountId
) implements AeronResponse {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
