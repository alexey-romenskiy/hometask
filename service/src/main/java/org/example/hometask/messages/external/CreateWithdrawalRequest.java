package org.example.hometask.messages.external;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateWithdrawalRequest(
        @NotNull UUID withdrawalUuid,
        @NotNull String address,
        @NotNull BigDecimal amount
) implements WithdrawalRequest {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
