package org.example.hometask.messages.response;

import org.example.hometask.messages.WithdrawalState;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record WithdrawalDataAeronResponse(
        long trackingId,
        @NotNull BigDecimal amount,
        @NotNull WithdrawalState state
) implements AeronResponse {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
