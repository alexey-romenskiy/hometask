package org.example.hometask.messages.request;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record CreateAccountAeronRequest(
        long trackingId,
        @NotNull BigDecimal initialAmount
) implements AeronRequest {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
