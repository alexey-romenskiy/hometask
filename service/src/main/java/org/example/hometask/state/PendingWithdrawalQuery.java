package org.example.hometask.state;

import org.example.hometask.messages.WithdrawalState;
import org.jetbrains.annotations.NotNull;

public record PendingWithdrawalQuery(
        @NotNull PendingOperation pendingOperation,
        long withdrawalId
) {

    public void completed(@NotNull WithdrawalState state) {
        pendingOperation.completed(withdrawalId);
    }

    public void failed() {
        pendingOperation.completed(withdrawalId);
    }
}
