package org.example.hometask.messages.request;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record AccountWithdrawAeronRequest(
        long trackingId,
        long fromAccountId,
        @NotNull String toAddress,
        @NotNull BigDecimal amount
) implements AeronRequest {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
