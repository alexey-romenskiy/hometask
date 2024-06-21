package org.example.hometask.messages.external;

import org.example.hometask.external.WithdrawalService;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record CreateWithdrawalRequest(
        @NotNull WithdrawalService.WithdrawalId id,
        @NotNull WithdrawalService.Address address,
        @NotNull BigDecimal amount
) implements WithdrawalRequest {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
