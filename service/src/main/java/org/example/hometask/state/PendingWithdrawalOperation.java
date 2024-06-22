package org.example.hometask.state;

import org.example.hometask.disruptor.Publisher;
import org.example.hometask.external.WithdrawalService;
import org.example.hometask.messages.external.CreateWithdrawalRequest;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class PendingWithdrawalOperation extends AbstractPendingAccountCreditOperation {

    @NotNull
    private final Repository repository;

    @NotNull
    private final String toAddress;

    public PendingWithdrawalOperation(
            @NotNull Account account,
            @NotNull Publisher publisher,
            int sessionId,
            long trackingId,
            @NotNull BigDecimal amount,
            @NotNull Repository repository,
            @NotNull String toAddress
    ) {
        super(account, publisher, sessionId, trackingId, amount);
        this.repository = repository;
        this.toAddress = toAddress;
    }

    @Override
    protected void performOperation() {
        account.adjustReserved(amount);
        final var withdrawal = new Withdrawal(
                publisher,
                repository.nextWithdrawalId(),
                account,
                amount,
                new WithdrawalService.Address(toAddress),
                generateUuid(),
                sessionId,
                trackingId
        );
        repository.withdrawals.put(withdrawal.getId(), withdrawal);
        repository.withdrawalsByUuid.put(withdrawal.getUuid(), withdrawal);
        publisher.publish(new CreateWithdrawalRequest(withdrawal.getUuid(), toAddress, amount));
    }

    @NotNull
    private UUID generateUuid() {
        // UUIDs are statistically unique, yet we try to achieve even stronger guarantees
        while (true) {
            // That would be a crime for deterministic state machine in case of Aeron Cluster or similar solutions.
            // If we want to support a deterministic replay for event log, we have to replace UUID.randomUUID() with
            // some deterministic pseudo-random implementation which could be seeded via event log. It may be risky
            // to use monotonic UUID generator here since we do not know if anybody else outside our service is
            // generating UUIDs for this external API. We'd like this API to have some means to separate our UUIDs from
            // others, possibly by introducing some unique "naming prefix" belonging to our service. In reality, some
            // real-world crypto exchange APIs do not provide this. Opaque variable-length strings like ClOrdID,
            // constructed from some unique prefix and monotonic sequence number would be better than UUIDs here,
            // but we can't change the imposed rules.
            final var uuid = UUID.randomUUID();
            if (!repository.withdrawalsByUuid.containsKey(uuid)) {
                return uuid;
            }
        }
    }
}
