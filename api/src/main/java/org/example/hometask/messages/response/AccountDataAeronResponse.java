package org.example.hometask.messages.response;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record AccountDataAeronResponse(
        long trackingId,
        @NotNull BigDecimal availableAmount,
        @NotNull BigDecimal reservedAmount
) implements AeronResponse {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
