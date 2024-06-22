package org.example.hometask.state;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public abstract class AbstractAccountPendingOperation {

    @NotNull
    protected final Account account;

    public AbstractAccountPendingOperation(@NotNull Account account) {
        this.account = account;
    }

    protected boolean tryUpdate() {

        final var withdrawals = account.getPendingWithdrawals().values();
        final var pendingWithdrawalIds = new HashSet<>(withdrawals.size());

        for (final var withdrawal : withdrawals) {
            pendingWithdrawalIds.add(withdrawal.getId());
            withdrawal.updateState(() -> {
                pendingWithdrawalIds.remove(withdrawal.getId());
                if (pendingWithdrawalIds.isEmpty()) {
                    nowUpdated();
                }
            });
        }

        return pendingWithdrawalIds.isEmpty();
    }

    protected abstract void nowUpdated();
}
