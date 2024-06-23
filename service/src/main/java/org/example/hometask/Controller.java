package org.example.hometask;

import org.example.hometask.disruptor.Publisher;
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
import org.example.hometask.messages.response.InvalidAmountAeronResponse;
import org.example.hometask.messages.response.NoSuchEntityAeronResponse;
import org.example.hometask.messages.response.SameAccountAeronResponse;
import org.example.hometask.state.Account;
import org.example.hometask.state.PendingQueryAccountOperation;
import org.example.hometask.state.PendingQueryWithdrawalOperation;
import org.example.hometask.state.PendingTransferOperation;
import org.example.hometask.state.PendingWithdrawalOperation;
import org.example.hometask.state.Repository;
import org.example.hometask.state.Withdrawal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class Controller {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final Publisher publisher;

    private final Repository repository = new Repository();

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
                getWithdrawal(event.withdrawalUuid()).createDone();
                return null;
            }

            @Override
            public Void visit(@NotNull CreateWithdrawalDuplicationFailureEvent event) {
                // We assume nobody else could use our UUID and the withdrawal was just created earlier.
                // This may occur after temporary network failure or restart of our service.
                logger.warn("Duplicated withdrawal UUID encountered: {}", event.withdrawalUuid());
                getWithdrawal(event.withdrawalUuid()).createDone();
                return null;
            }

            @Override
            public Void visit(@NotNull QueryWithdrawalSuccessEvent event) {
                getWithdrawal(event.withdrawalUuid()).queryDone(event.state());
                return null;
            }

            @Override
            public Void visit(@NotNull QueryWithdrawalUnknownIdFailureEvent event) {
                logger.error("Failed to update withdrawal state for UUID: {}", event.withdrawalUuid());
                getWithdrawal(event.withdrawalUuid()).queryFailed();
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

    @NotNull
    private Withdrawal getWithdrawal(@NotNull UUID withdrawalUuid) {
        return requireNonNull(repository.withdrawalsByUuid.get(withdrawalUuid));
    }

    private void createAccount(@NotNull InboundAeronMessageEvent event, @NotNull CreateAccountAeronRequest message) {

        final var amount = message.initialAmount();
        if (amount.signum() < 0) {
            publisher.publish(event.sessionId(), new InvalidAmountAeronResponse(message.trackingId()));
            return;
        }

        final var account = new Account(repository.nextAccountId(), amount);
        repository.accounts.put(account.getId(), account);
        publisher.publish(event.sessionId(), new AccountCreatedAeronResponse(message.trackingId(), account.getId()));
    }

    private void accountTransfer(
            @NotNull InboundAeronMessageEvent event,
            @NotNull AccountTransferAeronRequest message
    ) {
        final var fromAccount = repository.accounts.get(message.fromAccountId());
        final var toAccount = repository.accounts.get(message.toAccountId());

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

        new PendingTransferOperation(
                fromAccount,
                publisher,
                event.sessionId(),
                message.trackingId(),
                amount,
                toAccount
        ).execute();
    }

    private void accountWithdrawal(
            @NotNull InboundAeronMessageEvent event,
            @NotNull AccountWithdrawAeronRequest message
    ) {
        final var fromAccount = repository.accounts.get(message.fromAccountId());

        if (fromAccount == null) {
            publisher.publish(event.sessionId(), new NoSuchEntityAeronResponse(message.trackingId()));
            return;
        }

        final var amount = message.amount();
        if (amount.signum() <= 0) {
            publisher.publish(event.sessionId(), new InvalidAmountAeronResponse(message.trackingId()));
            return;
        }

        new PendingWithdrawalOperation(
                fromAccount,
                publisher,
                event.sessionId(),
                message.trackingId(),
                amount,
                repository,
                message.toAddress()
        ).execute();
    }

    private void queryAccount(
            @NotNull InboundAeronMessageEvent event,
            @NotNull QueryAccountAeronRequest message
    ) {
        final var account = repository.accounts.get(message.accountId());

        if (account == null) {
            publisher.publish(event.sessionId(), new NoSuchEntityAeronResponse(message.trackingId()));
            return;
        }

        new PendingQueryAccountOperation(
                account,
                publisher,
                event.sessionId(),
                message.trackingId()
        ).execute();
    }

    private void queryWithdrawal(
            @NotNull InboundAeronMessageEvent event,
            @NotNull QueryWithdrawalAeronRequest message
    ) {
        final var withdrawal = repository.withdrawals.get(message.withdrawalId());

        // withdrawal.isCreated() == false implies we did not say ID of this withdrawal to the client
        // also we cannot query state of withdrawal until it created
        if (withdrawal == null || !withdrawal.isCreated()) {
            publisher.publish(event.sessionId(), new NoSuchEntityAeronResponse(message.trackingId()));
            return;
        }

        new PendingQueryWithdrawalOperation(
                withdrawal,
                publisher,
                event.sessionId(),
                message.trackingId()
        ).execute();
    }
}
