package org.example.hometask.messages.external;

import org.example.hometask.external.WithdrawalService;
import org.jetbrains.annotations.NotNull;

public record QueryWithdrawalRequest(
        @NotNull WithdrawalService.WithdrawalId id
) implements WithdrawalRequest {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
