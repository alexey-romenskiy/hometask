package org.example.hometask.state;

import org.example.hometask.messages.WithdrawalState;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractWithdrawalPendingOperation {

    @NotNull
    protected final Withdrawal withdrawal;

    public AbstractWithdrawalPendingOperation(@NotNull Withdrawal withdrawal) {
        this.withdrawal = withdrawal;
    }

    protected boolean tryUpdate() {

        if (withdrawal.getState() != WithdrawalState.PROCESSING) {
            return true;
        }

        withdrawal.updateState(this::nowUpdated);
        return false;
    }

    protected abstract void nowUpdated();
}
