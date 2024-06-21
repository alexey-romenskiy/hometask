package org.example.hometask.state;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class PendingOperation {

    @NotNull
    private final Set<Long> pendingWithdrawalIds;

    public PendingOperation(@NotNull Set<Long> pendingWithdrawalIds) {
        this.pendingWithdrawalIds = pendingWithdrawalIds;
    }

    public void completed(long withdrawalId) {
        pendingWithdrawalIds.remove(withdrawalId);
        if (pendingWithdrawalIds.isEmpty()) {
            withdrawalStatesUpdated();
        }
    }

    protected abstract void withdrawalStatesUpdated();
}
