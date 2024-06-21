package org.example.hometask.messages.disruptor;

import org.example.hometask.external.WithdrawalService;
import org.example.hometask.messages.WithdrawalState;
import org.jetbrains.annotations.NotNull;

public record QueryWithdrawalSuccessEvent(
        @NotNull WithdrawalService.WithdrawalId withdrawalId,
        @NotNull WithdrawalState state
) implements Event {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
