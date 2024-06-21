package org.example.hometask.messages.request;

import org.jetbrains.annotations.NotNull;

public record QueryWithdrawalAeronRequest(
        long trackingId,
        long withdrawalId
) implements AeronRequest {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
