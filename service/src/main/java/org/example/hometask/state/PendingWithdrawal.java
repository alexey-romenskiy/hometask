package org.example.hometask.state;

import org.example.hometask.Controller;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Set;

public class PendingWithdrawal extends PendingOperation {

    @NotNull
    private final Controller controller;

    private final int sessionId;

    private final long trackingId;

    @NotNull
    private final Account fromAccount;

    @NotNull
    private final String toAddress;

    @NotNull
    private final BigDecimal amount;

    public PendingWithdrawal(
            @NotNull Controller controller,
            int sessionId,
            long trackingId,
            @NotNull Account fromAccount,
            @NotNull String toAddress,
            @NotNull BigDecimal amount,
            @NotNull Set<Long> pendingWithdrawalIds
    ) {
        super(pendingWithdrawalIds);
        this.controller = controller;
        this.sessionId = sessionId;
        this.trackingId = trackingId;
        this.fromAccount = fromAccount;
        this.toAddress = toAddress;
        this.amount = amount;
    }

    @Override
    protected void withdrawalStatesUpdated() {
        controller.continueAccountWithdrawal(sessionId, trackingId, fromAccount, toAddress, amount);
    }
}