package org.example.hometask.messages.disruptor;

import org.example.hometask.external.WithdrawalService;
import org.jetbrains.annotations.NotNull;

public record CreateWithdrawalDuplicationFailureEvent(
        @NotNull WithdrawalService.WithdrawalId withdrawalId
) implements Event {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
