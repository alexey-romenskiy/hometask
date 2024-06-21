package org.example.hometask.state;

import org.example.hometask.disruptor.Publisher;
import org.example.hometask.external.WithdrawalService;
import org.example.hometask.messages.WithdrawalState;
import org.example.hometask.messages.external.CreateWithdrawalRequest;
import org.example.hometask.messages.external.QueryWithdrawalRequest;
import org.example.hometask.messages.response.AccountWithdrawalDoneAeronResponse;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.example.hometask.messages.WithdrawalState.PROCESSING;

public class Withdrawal {

    @NotNull
    private final Publisher publisher;

    private final long id;

    @NotNull
    private final Account account;

    @NotNull
    private final BigDecimal amount;

    @NotNull
    private final WithdrawalService.Address address;

    private boolean created;

    /**
     * <code>true</code> if there is a pending external request for the state.
     * Only one pending external request may exist for any given withdrawal.
     */
    private boolean queryPending;

    @NotNull
    private final UUID uuid;

    private final int sessionId;

    private final long trackingId;

    @NotNull
    private WithdrawalState state = PROCESSING;

    /**
     * All delayed operations awaiting the current pending external request.
     */
    private ArrayList<PendingWithdrawalQuery> pendingQueries = new ArrayList<>();

    /**
     * All delayed operations awaiting the state update,
     * but the current pending external request would return a stale state for them (the problem is
     * the current external request had been issued BEFORE those operations created).
     * Those delayed operations will be handled by the next external request.
     */
    private ArrayList<PendingWithdrawalQuery> queuedQueries = new ArrayList<>();

    public Withdrawal(
            @NotNull Publisher publisher,
            long id,
            @NotNull Account account,
            @NotNull BigDecimal amount,
            @NotNull WithdrawalService.Address address,
            @NotNull UUID uuid,
            int sessionId,
            long trackingId
    ) {
        this.publisher = publisher;
        this.id = id;
        this.account = account;
        this.amount = amount;
        this.address = address;
        this.uuid = uuid;
        this.sessionId = sessionId;
        this.trackingId = trackingId;
        publishCreate();
    }

    public long getId() {
        return id;
    }

    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    @NotNull
    public Account getAccount() {
        return account;
    }

    @NotNull
    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isCreated() {
        return created;
    }

    @NotNull
    public WithdrawalState getState() {
        return state;
    }

    public void updateState(@NotNull PendingWithdrawalQuery query) {

        if (state != PROCESSING) {
            throw new IllegalStateException();
        }

        if (queryPending) {
            queuedQueries.add(query);
        } else {
            queryPending = true;
            pendingQueries.add(query);
            publishQuery();
        }
    }

    public void createDone() {
        // from now, we can query its state:
        created = true;
        account.withdrawalCreated(this);
        publisher.publish(sessionId, new AccountWithdrawalDoneAeronResponse(trackingId, id));
    }

    public void queryDone(@NotNull WithdrawalState state) {
        this.state = state;
        if (state != PROCESSING) {
            account.withdrawalCompleted(this);
        }
        for (final var query : pendingQueries) {
            query.completed(state);
        }
        roll();
    }

    public void queryFailed() {
        for (final var query : pendingQueries) {
            query.failed();
        }
        roll();
    }

    private void roll() {
        pendingQueries.clear();
        if (queuedQueries.isEmpty()) {
            queryPending = false;
        } else {
            if (state == PROCESSING) {
                final var swap = pendingQueries;
                pendingQueries = queuedQueries;
                queuedQueries = swap;
                publishQuery();
            } else {
                for (final var query : queuedQueries) {
                    query.completed(state);
                }
                queuedQueries.clear();
            }
        }
    }

    private void publishQuery() {
        publisher.publish(new QueryWithdrawalRequest(new WithdrawalService.WithdrawalId(uuid)));
    }

    private void publishCreate() {
        publisher.publish(new CreateWithdrawalRequest(new WithdrawalService.WithdrawalId(uuid), address, amount));
    }
}
