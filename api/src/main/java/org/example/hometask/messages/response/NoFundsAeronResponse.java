package org.example.hometask.messages.response;

import org.jetbrains.annotations.NotNull;

public record NoFundsAeronResponse(
        long trackingId
) implements AeronResponse {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
