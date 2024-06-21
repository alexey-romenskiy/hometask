package org.example.hometask.state;

import org.example.hometask.Controller;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PendingQueryAccount extends PendingOperation {

    @NotNull
    private final Controller controller;

    private final int sessionId;

    private final long trackingId;

    @NotNull
    private final Account account;

    public PendingQueryAccount(
            @NotNull Controller controller,
            int sessionId,
            long trackingId,
            @NotNull Account account,
            @NotNull Set<Long> pendingWithdrawalIds
    ) {
        super(pendingWithdrawalIds);
        this.controller = controller;
        this.sessionId = sessionId;
        this.trackingId = trackingId;
        this.account = account;
    }

    @Override
    protected void withdrawalStatesUpdated() {
        controller.continueQueryAccount(sessionId, trackingId, account);
    }
}
