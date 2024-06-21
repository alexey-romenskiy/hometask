package org.example.hometask.state;

import org.example.hometask.Controller;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PendingQueryWithdrawal extends PendingOperation {

    @NotNull
    private final Controller controller;

    private final int sessionId;

    private final long trackingId;

    @NotNull
    private final Withdrawal withdrawal;

    public PendingQueryWithdrawal(
            @NotNull Controller controller,
            int sessionId,
            long trackingId,
            @NotNull Withdrawal withdrawal,
            @NotNull Set<Long> pendingWithdrawalIds
    ) {
        super(pendingWithdrawalIds);
        this.controller = controller;
        this.sessionId = sessionId;
        this.trackingId = trackingId;
        this.withdrawal = withdrawal;
    }

    @Override
    protected void withdrawalStatesUpdated() {
        controller.continueQueryWithdrawal(sessionId, trackingId, withdrawal);
    }
}
