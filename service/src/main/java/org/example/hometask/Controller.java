package org.example.hometask;

import org.example.hometask.disruptor.Publisher;
import org.example.hometask.external.WithdrawalService;
import org.example.hometask.messages.WithdrawalState;
import org.example.hometask.messages.disruptor.CreateWithdrawalDuplicationFailureEvent;
import org.example.hometask.messages.disruptor.CreateWithdrawalSuccessEvent;
import org.example.hometask.messages.disruptor.Event;
import org.example.hometask.messages.disruptor.InboundAeronMessageEvent;
import org.example.hometask.messages.disruptor.QueryWithdrawalSuccessEvent;
import org.example.hometask.messages.disruptor.QueryWithdrawalUnknownIdFailureEvent;
import org.example.hometask.messages.request.AccountTransferAeronRequest;
import org.example.hometask.messages.request.AccountWithdrawAeronRequest;
import org.example.hometask.messages.request.AeronRequest;
import org.example.hometask.messages.request.CreateAccountAeronRequest;
import org.example.hometask.messages.request.QueryAccountAeronRequest;
import org.example.hometask.messages.request.QueryWithdrawalAeronRequest;
import org.example.hometask.messages.response.AccountCreatedAeronResponse;
import org.example.hometask.messages.response.AccountDataAeronResponse;
import org.example.hometask.messages.response.AccountTransferDoneAeronResponse;
import org.example.hometask.messages.response.InvalidAmountAeronResponse;
import org.example.hometask.messages.response.NoFundsAeronResponse;
import org.example.hometask.messages.response.NoSuchEntityAeronResponse;
import org.example.hometask.messages.response.SameAccountAeronResponse;
import org.example.hometask.messages.response.WithdrawalDataAeronResponse;
import org.example.hometask.state.Account;
import org.example.hometask.state.PendingQueryAccount;
import org.example.hometask.state.PendingQueryWithdrawal;
import org.example.hometask.state.PendingTransfer;
import org.example.hometask.state.PendingWithdrawal;
import org.example.hometask.state.PendingWithdrawalQuery;
import org.example.hometask.state.Withdrawal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class Controller {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final Publisher publisher;

    private final Map<Long, Account> accounts = new HashMap<>();

    private final Map<Long, Withdrawal> withdrawals = new HashMap<>();

    private final Map<UUID, Withdrawal> withdrawalsByUuid = new HashMap<>();

    private long accountSequence;

    private long withdrawalSequence;

    public Controller(@NotNull Publisher publisher) {
        this.publisher = publisher;
    }

    public void accept(@NotNull Event event) {

        event.accept(new Event.Visitor<Void, RuntimeException>() {
            @Override
            public Void visit(@NotNull InboundAeronMessageEvent event) {
                onInboundAeronMessageEvent(event);
                return null;
            }

            @Override
            public Void visit(@NotNull CreateWithdrawalSuccessEvent event) {
                withdrawalsByUuid.get(event.withdrawalId().value()).createDone();
                return null;
            }

            @Override
            public Void visit(@NotNull CreateWithdrawalDuplicationFailureEvent event) {
                // We assume nobody else could use iur UUID and the withdrawal was just created earlier.
                // This may occur after temporary network failure or restart of our service.
                logger.warn("Duplicated withdrawal UUID encountered: {}", event.withdrawalId().value());
                withdrawalsByUuid.get(event.withdrawalId().value()).createDone();
                return null;
            }

            @Override
            public Void visit(@NotNull QueryWithdrawalSuccessEvent event) {
                withdrawalsByUuid.get(event.withdrawalId().value()).queryDone(event.state());
                return null;
            }

            @Override
            public Void visit(@NotNull QueryWithdrawalUnknownIdFailureEvent event) {
                logger.error("Failed to update withdrawal state for UUID: {}", event.withdrawalId().value());
                withdrawalsByUuid.get(event.withdrawalId().value()).queryFailed();
                return null;
            }
        });
    }

    private void onInboundAeronMessageEvent(@NotNull InboundAeronMessageEvent event) {
        event.message().accept(new AeronRequest.Visitor<Void, RuntimeException>() {
            @Override
            public Void visit(@NotNull CreateAccountAeronRequest message) {
                createAccount(event, message);
                return null;
            }

            @Override
            public Void visit(@NotNull QueryAccountAeronRequest message) {
                queryAccount(event, message);
                return null;
            }

            @Override
            public Void visit(@NotNull AccountTransferAeronRequest message) {
                accountTransfer(event, message);
                return null;
            }

            @Override
            public Void visit(@NotNull AccountWithdrawAeronRequest message) {
                accountWithdrawal(event, message);
                return null;
            }

            @Override
            public Void visit(@NotNull QueryWithdrawalAeronRequest message) {
                queryWithdrawal(event, message);
                return null;
            }
        });
    }

    private void createAccount(@NotNull InboundAeronMessageEvent event, @NotNull CreateAccountAeronRequest message) {

        final var amount = message.initialAmount();
        if (amount.signum() < 0) {
            publisher.publish(event.sessionId(), new InvalidAmountAeronResponse(message.trackingId()));
            return;
        }

        final var account = new Account(++accountSequence, amount);
        accounts.put(account.getId(), account);
        publisher.publish(event.sessionId(), new AccountCreatedAeronResponse(message.trackingId(), account.getId()));
    }

    private void accountTransfer(
            @NotNull InboundAeronMessageEvent event,
            @NotNull AccountTransferAeronRequest message
    ) {
        final var fromAccount = accounts.get(message.fromAccountId());
        final var toAccount = accounts.get(message.toAccountId());

        if (fromAccount == null || toAccount == null) {
            publisher.publish(event.sessionId(), new NoSuchEntityAeronResponse(message.trackingId()));
            return;
        }

        if (message.fromAccountId() == message.toAccountId()) {
            publisher.publish(event.sessionId(), new SameAccountAeronResponse(message.trackingId()));
            return;
        }

        final var amount = message.amount();
        if (amount.signum() <= 0) {
            publisher.publish(event.sessionId(), new InvalidAmountAeronResponse(message.trackingId()));
            return;
        }

        if (fromAccount.reduce(amount)) {
            toAccount.adjustAvailable(amount);
            publisher.publish(event.sessionId(), new AccountTransferDoneAeronResponse(message.trackingId()));
        } else {
            final var pendingWithdrawals = fromAccount.getPendingWithdrawals();
            if (pendingWithdrawals.isEmpty() || amount.compareTo(fromAccount.getPendingAmount()) > 0) {
                publisher.publish(event.sessionId(), new NoFundsAeronResponse(message.trackingId()));
            } else {
                final var pendingWithdrawalIds = new HashSet<Long>();
                final var pendingTransfer =
                        new PendingTransfer(this, event.sessionId(), message.trackingId(), fromAccount, toAccount,
                                amount, pendingWithdrawalIds);
                for (final var withdrawal : pendingWithdrawals.values()) {
                    pendingWithdrawalIds.add(withdrawal.getId());
                    withdrawal.updateState(new PendingWithdrawalQuery(pendingTransfer, withdrawal.getId()));
                }
            }
        }
    }

    public void continueAccountTransfer(
            int sessionId,
            long trackingId,
            @NotNull Account fromAccount,
            @NotNull Account toAccount,
            @NotNull BigDecimal amount
    ) {
        if (fromAccount.reduce(amount)) {
            toAccount.adjustAvailable(amount);
            publisher.publish(sessionId, new AccountTransferDoneAeronResponse(trackingId));
        } else {
            publisher.publish(sessionId, new NoFundsAeronResponse(trackingId));
        }
    }

    private void accountWithdrawal(
            @NotNull InboundAeronMessageEvent event,
            @NotNull AccountWithdrawAeronRequest message
    ) {
        final var fromAccount = accounts.get(message.fromAccountId());

        if (fromAccount == null) {
            publisher.publish(event.sessionId(), new NoSuchEntityAeronResponse(message.trackingId()));
            return;
        }

        final var amount = message.amount();
        if (amount.signum() <= 0) {
            publisher.publish(event.sessionId(), new InvalidAmountAeronResponse(message.trackingId()));
            return;
        }

        if (fromAccount.reduce(amount)) {
            fromAccount.adjustReserved(amount);
            final var withdrawal = new Withdrawal(publisher, ++withdrawalSequence, fromAccount, amount,
                    new WithdrawalService.Address(message.toAddress()), generateUuid(), event.sessionId(),
                    message.trackingId());
            withdrawals.put(withdrawal.getId(), withdrawal);
            withdrawalsByUuid.put(withdrawal.getUuid(), withdrawal);
        } else {
            final var pendingWithdrawals = fromAccount.getPendingWithdrawals();
            if (pendingWithdrawals.isEmpty() || amount.compareTo(fromAccount.getPendingAmount()) > 0) {
                publisher.publish(event.sessionId(), new NoFundsAeronResponse(message.trackingId()));
            } else {
                final var pendingWithdrawalIds = new HashSet<Long>();
                final var pendingWithdrawal =
                        new PendingWithdrawal(this, event.sessionId(), message.trackingId(), fromAccount,
                                message.toAddress(), amount, pendingWithdrawalIds);
                for (final var withdrawal : pendingWithdrawals.values()) {
                    pendingWithdrawalIds.add(withdrawal.getId());
                    withdrawal.updateState(new PendingWithdrawalQuery(pendingWithdrawal, withdrawal.getId()));
                }
            }
        }
    }

    public void continueAccountWithdrawal(
            int sessionId,
            long trackingId,
            @NotNull Account fromAccount,
            @NotNull String toAddress,
            @NotNull BigDecimal amount
    ) {
        if (fromAccount.reduce(amount)) {
            fromAccount.adjustReserved(amount);
            final var withdrawal = new Withdrawal(publisher, ++withdrawalSequence, fromAccount, amount,
                    new WithdrawalService.Address(toAddress), generateUuid(), sessionId, trackingId);
            withdrawals.put(withdrawal.getId(), withdrawal);
            withdrawalsByUuid.put(withdrawal.getUuid(), withdrawal);
        } else {
            publisher.publish(sessionId, new NoFundsAeronResponse(trackingId));
        }
    }

    private void queryAccount(
            @NotNull InboundAeronMessageEvent event,
            @NotNull QueryAccountAeronRequest message
    ) {
        final var account = accounts.get(message.accountId());

        if (account == null) {
            publisher.publish(event.sessionId(), new NoSuchEntityAeronResponse(message.trackingId()));
            return;
        }

        final var pendingWithdrawals = account.getPendingWithdrawals();
        if (pendingWithdrawals.isEmpty()) {
            continueQueryAccount(event.sessionId(), message.trackingId(), account);
        } else {
            final var pendingWithdrawalIds = new HashSet<Long>();
            final var pendingQueryAccount =
                    new PendingQueryAccount(this, event.sessionId(), message.trackingId(), account,
                            pendingWithdrawalIds);
            for (final var withdrawal : pendingWithdrawals.values()) {
                pendingWithdrawalIds.add(withdrawal.getId());
                withdrawal.updateState(new PendingWithdrawalQuery(pendingQueryAccount, withdrawal.getId()));
            }
        }
    }

    public void continueQueryAccount(
            int sessionId,
            long trackingId,
            @NotNull Account account
    ) {
        publisher.publish(sessionId,
                new AccountDataAeronResponse(trackingId, account.getAvailableAmount(), account.getReservedAmount()));
    }

    private void queryWithdrawal(
            @NotNull InboundAeronMessageEvent event,
            @NotNull QueryWithdrawalAeronRequest message
    ) {
        final var withdrawal = withdrawals.get(message.withdrawalId());

        // withdrawal.isCreated() == false implies we did not say ID of this withdrawal to the client
        // also we cannot query state of withdrawal until it created
        if (withdrawal == null || !withdrawal.isCreated()) {
            publisher.publish(event.sessionId(), new NoSuchEntityAeronResponse(message.trackingId()));
            return;
        }

        if (withdrawal.getState() == WithdrawalState.PROCESSING) {
            final var pendingWithdrawalIds = new HashSet<Long>();
            final var pendingQueryWithdrawal =
                    new PendingQueryWithdrawal(this, event.sessionId(), message.trackingId(), withdrawal,
                            pendingWithdrawalIds);
            pendingWithdrawalIds.add(withdrawal.getId());
            withdrawal.updateState(new PendingWithdrawalQuery(pendingQueryWithdrawal, withdrawal.getId()));
        } else {
            continueQueryWithdrawal(event.sessionId(), message.trackingId(), withdrawal);
        }
    }

    public void continueQueryWithdrawal(
            int sessionId,
            long trackingId,
            @NotNull Withdrawal withdrawal
    ) {
        publisher.publish(sessionId,
                new WithdrawalDataAeronResponse(trackingId, withdrawal.getAmount(), withdrawal.getState()));
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
            if (!withdrawalsByUuid.containsKey(uuid)) {
                return uuid;
            }
        }
    }
}
