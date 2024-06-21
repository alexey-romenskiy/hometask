package org.example.hometask;

import org.example.hometask.disruptor.EventContext;
import org.example.hometask.disruptor.EventHolder;
import org.example.hometask.disruptor.Publisher;
import org.example.hometask.external.WithdrawalService;
import org.example.hometask.messages.disruptor.CreateWithdrawalDuplicationFailureEvent;
import org.example.hometask.messages.disruptor.CreateWithdrawalSuccessEvent;
import org.example.hometask.messages.disruptor.Event;
import org.example.hometask.messages.disruptor.InboundAeronMessageEvent;
import org.example.hometask.messages.disruptor.QueryWithdrawalSuccessEvent;
import org.example.hometask.messages.disruptor.QueryWithdrawalUnknownIdFailureEvent;
import org.example.hometask.messages.external.CreateWithdrawalRequest;
import org.example.hometask.messages.external.QueryWithdrawalRequest;
import org.example.hometask.messages.request.AccountTransferAeronRequest;
import org.example.hometask.messages.request.AccountWithdrawAeronRequest;
import org.example.hometask.messages.request.CreateAccountAeronRequest;
import org.example.hometask.messages.request.QueryAccountAeronRequest;
import org.example.hometask.messages.request.QueryWithdrawalAeronRequest;
import org.example.hometask.messages.response.AccountCreatedAeronResponse;
import org.example.hometask.messages.response.AccountDataAeronResponse;
import org.example.hometask.messages.response.AccountTransferDoneAeronResponse;
import org.example.hometask.messages.response.AccountWithdrawalDoneAeronResponse;
import org.example.hometask.messages.response.InvalidAmountAeronResponse;
import org.example.hometask.messages.response.NoFundsAeronResponse;
import org.example.hometask.messages.response.NoSuchEntityAeronResponse;
import org.example.hometask.messages.response.OutboundAeronMessageEnvelope;
import org.example.hometask.messages.response.SameAccountAeronResponse;
import org.example.hometask.messages.response.WithdrawalDataAeronResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.example.hometask.messages.WithdrawalState.COMPLETED;
import static org.example.hometask.messages.WithdrawalState.FAILED;
import static org.example.hometask.messages.WithdrawalState.PROCESSING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("SameParameterValue")
public class ControllerTest {

    private static final int SESSION_ID = 123;
    private static final int TRACKING_ID = 234;
    private static final BigDecimal INITIAL_AMOUNT = new BigDecimal("345.678");
    private static final BigDecimal TRANSFER_AMOUNT1 = new BigDecimal("100");
    private static final BigDecimal TRANSFER_AMOUNT2 = new BigDecimal("101");
    private static final BigDecimal TRANSFER_AMOUNT3 = new BigDecimal("102");
    private static final BigDecimal TRANSFER_AMOUNT4 = new BigDecimal("103");
    private static final BigDecimal TRANSFER_AMOUNT5 = new BigDecimal("500");
    private static final String WITHDRAW_ADDRESS = "foobar";

    private final ArrayList<WithdrawalService.WithdrawalId> withdrawalIds = new ArrayList<>();
    private Controller controller;
    private EventHolder holder;
    private long trackingSequence;

    @Before
    public void setUp() {
        final var context = new EventContext();
        controller = new Controller(new Publisher(context));
        holder = new EventHolder();
        context.holder = holder;
        trackingSequence = TRACKING_ID;
        withdrawalIds.clear();
    }

    @Test
    public void createAndCheckAccount() {
        createAccount(1, INITIAL_AMOUNT);
        checkBalance(1, INITIAL_AMOUNT, ZERO);
    }

    @Test
    public void successfulAccountTransfer() {
        createAccount(1, INITIAL_AMOUNT);
        createAccount(2, ZERO);
        expectTransferSuccess();
        checkBalance(1, INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT1), ZERO);
        checkBalance(2, TRANSFER_AMOUNT1, ZERO);
    }

    @Test
    public void failedAccountTransfer() {
        createAccount(1, INITIAL_AMOUNT);
        createAccount(2, ZERO);
        expectTransferNoFunds(INITIAL_AMOUNT.add(TRANSFER_AMOUNT1));
        checkBalance(1, INITIAL_AMOUNT, ZERO);
        checkBalance(2, ZERO, ZERO);
    }

    @Test
    public void successfulAccountTransferWithPendingWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        createAccount(2, ZERO);
        initiateWithdrawal(1, TRANSFER_AMOUNT1);
        final var trackingId2 = initiateWithdrawal(1, TRANSFER_AMOUNT2);
        final var trackingId3 = initiateWithdrawal(1, TRANSFER_AMOUNT3);
        withdrawalCreated(2, trackingId2);
        withdrawalDuplicated(3, trackingId3);
        final var trackingId4 = initiateTransferWithPendingWithdrawals(1, 2);

        process(new QueryWithdrawalUnknownIdFailureEvent(getUuid(2)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new QueryWithdrawalSuccessEvent(getUuid(3), FAILED));
        assertEquals(new OutboundAeronMessageEnvelope(SESSION_ID, new AccountTransferDoneAeronResponse(trackingId4)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(List.of(new QueryWithdrawalRequest(getUuid(2))), holder.withdrawalRequests);

        process(new QueryWithdrawalSuccessEvent(getUuid(2), PROCESSING));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT1).subtract(TRANSFER_AMOUNT2)
                                        .subtract(TRANSFER_AMOUNT4),
                                TRANSFER_AMOUNT1.add(TRANSFER_AMOUNT2)
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;

        checkBalance(2, TRANSFER_AMOUNT4, ZERO);
    }

    @Test
    public void failedAccountTransferWithPendingWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        createAccount(2, ZERO);
        initiateWithdrawal(1, TRANSFER_AMOUNT1);
        final var trackingId2 = initiateWithdrawal(1, TRANSFER_AMOUNT2);
        final var trackingId3 = initiateWithdrawal(1, TRANSFER_AMOUNT3);
        withdrawalCreated(2, trackingId2);
        withdrawalDuplicated(3, trackingId3);
        final var trackingId4 = initiateTransferWithPendingWithdrawals(1, 2);

        process(new QueryWithdrawalUnknownIdFailureEvent(getUuid(2)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new QueryWithdrawalSuccessEvent(getUuid(3), COMPLETED));
        assertEquals(new OutboundAeronMessageEnvelope(SESSION_ID, new NoFundsAeronResponse(trackingId4)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(List.of(new QueryWithdrawalRequest(getUuid(2))), holder.withdrawalRequests);

        process(new QueryWithdrawalSuccessEvent(getUuid(2), PROCESSING));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT1).subtract(TRANSFER_AMOUNT2)
                                        .subtract(TRANSFER_AMOUNT3),
                                TRANSFER_AMOUNT1.add(TRANSFER_AMOUNT2)
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;

        checkBalance(2, ZERO, ZERO);
    }

    @Test
    public void failfastAccountTransferWithPendingWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        createAccount(2, ZERO);
        initiateWithdrawal(1, TRANSFER_AMOUNT1);
        final var trackingId2 = initiateWithdrawal(1, TRANSFER_AMOUNT2);
        final var trackingId3 = initiateWithdrawal(1, TRANSFER_AMOUNT3);
        withdrawalCreated(2, trackingId2);
        withdrawalDuplicated(3, trackingId3);

        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, 1, 2, TRANSFER_AMOUNT5)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoFundsAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(Set.of(new QueryWithdrawalRequest(getUuid(2)), new QueryWithdrawalRequest(getUuid(3))),
                new HashSet<>(holder.withdrawalRequests));

        process(new QueryWithdrawalSuccessEvent(getUuid(2), PROCESSING));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new QueryWithdrawalSuccessEvent(getUuid(3), PROCESSING));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT1).subtract(TRANSFER_AMOUNT2)
                                        .subtract(TRANSFER_AMOUNT3),
                                TRANSFER_AMOUNT1.add(TRANSFER_AMOUNT2).add(TRANSFER_AMOUNT3)
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;

        checkBalance(2, ZERO, ZERO);
    }

    @Test
    public void pendingAccountWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        final var trackingId1 = initiateWithdrawal(1, TRANSFER_AMOUNT1);
        withdrawalCreated(1, trackingId1);

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(List.of(new QueryWithdrawalRequest(getUuid(1))), holder.withdrawalRequests);

        process(new QueryWithdrawalSuccessEvent(getUuid(1), PROCESSING));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT1),
                                TRANSFER_AMOUNT1
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void successfulAccountWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        final var trackingId1 = initiateWithdrawal(1, TRANSFER_AMOUNT1);
        withdrawalCreated(1, trackingId1);

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(List.of(new QueryWithdrawalRequest(getUuid(1))), holder.withdrawalRequests);

        process(new QueryWithdrawalSuccessEvent(getUuid(1), COMPLETED));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT1),
                                ZERO
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void failedAccountWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        final var trackingId1 = initiateWithdrawal(1, TRANSFER_AMOUNT1);
        withdrawalCreated(1, trackingId1);

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(List.of(new QueryWithdrawalRequest(getUuid(1))), holder.withdrawalRequests);

        process(new QueryWithdrawalSuccessEvent(getUuid(1), FAILED));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT,
                                ZERO
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void rejectedAccountWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountWithdrawAeronRequest(trackingSequence, 1, WITHDRAW_ADDRESS, TRANSFER_AMOUNT5)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoFundsAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT,
                                ZERO
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void successfulAccountWithdrawalWithPendingWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        initiateWithdrawal(1, TRANSFER_AMOUNT1);
        final var trackingId2 = initiateWithdrawal(1, TRANSFER_AMOUNT2);
        final var trackingId3 = initiateWithdrawal(1, TRANSFER_AMOUNT3);
        withdrawalCreated(2, trackingId2);
        withdrawalDuplicated(3, trackingId3);
        final var trackingId4 = initiateWithdrawalWithPendingWithdrawals(1);

        process(new QueryWithdrawalUnknownIdFailureEvent(getUuid(2)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new QueryWithdrawalSuccessEvent(getUuid(3), FAILED));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(1, holder.withdrawalRequests.size());
        final var request = (CreateWithdrawalRequest) holder.withdrawalRequests.getFirst();
        assertEquals(WITHDRAW_ADDRESS, request.address().value());
        assertEquals(TRANSFER_AMOUNT4, request.amount());
        withdrawalIds.add(request.id());

        process(new CreateWithdrawalSuccessEvent(getUuid(4)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new AccountWithdrawalDoneAeronResponse(trackingId4, 4)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(Set.of(new QueryWithdrawalRequest(getUuid(2)), new QueryWithdrawalRequest(getUuid(4))),
                new HashSet<>(holder.withdrawalRequests));

        process(new QueryWithdrawalSuccessEvent(getUuid(2), PROCESSING));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new QueryWithdrawalSuccessEvent(getUuid(4), COMPLETED));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT1).subtract(TRANSFER_AMOUNT2)
                                        .subtract(TRANSFER_AMOUNT4),
                                TRANSFER_AMOUNT1.add(TRANSFER_AMOUNT2)
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;
    }

    @Test
    public void failedAccountWithdrawalWithPendingWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        initiateWithdrawal(1, TRANSFER_AMOUNT1);
        final var trackingId2 = initiateWithdrawal(1, TRANSFER_AMOUNT2);
        final var trackingId3 = initiateWithdrawal(1, TRANSFER_AMOUNT3);
        withdrawalCreated(2, trackingId2);
        withdrawalDuplicated(3, trackingId3);
        final var trackingId4 = initiateWithdrawalWithPendingWithdrawals(1);

        process(new QueryWithdrawalUnknownIdFailureEvent(getUuid(2)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new QueryWithdrawalSuccessEvent(getUuid(3), COMPLETED));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoFundsAeronResponse(trackingId4)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(List.of(new QueryWithdrawalRequest(getUuid(2))), holder.withdrawalRequests);

        process(new QueryWithdrawalSuccessEvent(getUuid(2), PROCESSING));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT1).subtract(TRANSFER_AMOUNT2)
                                        .subtract(TRANSFER_AMOUNT3),
                                TRANSFER_AMOUNT1.add(TRANSFER_AMOUNT2)
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;
    }

    @Test
    public void failfastAccountWithdrawalWithPendingWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        initiateWithdrawal(1, TRANSFER_AMOUNT1);
        final var trackingId2 = initiateWithdrawal(1, TRANSFER_AMOUNT2);
        final var trackingId3 = initiateWithdrawal(1, TRANSFER_AMOUNT3);
        withdrawalCreated(2, trackingId2);
        withdrawalDuplicated(3, trackingId3);

        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountWithdrawAeronRequest(trackingSequence, 1, WITHDRAW_ADDRESS, TRANSFER_AMOUNT5)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoFundsAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(Set.of(new QueryWithdrawalRequest(getUuid(2)), new QueryWithdrawalRequest(getUuid(3))),
                new HashSet<>(holder.withdrawalRequests));

        process(new QueryWithdrawalSuccessEvent(getUuid(2), PROCESSING));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());

        process(new QueryWithdrawalSuccessEvent(getUuid(3), PROCESSING));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new AccountDataAeronResponse(
                                trackingSequence,
                                INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT1).subtract(TRANSFER_AMOUNT2)
                                        .subtract(TRANSFER_AMOUNT3),
                                TRANSFER_AMOUNT1.add(TRANSFER_AMOUNT2).add(TRANSFER_AMOUNT3)
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;
    }

    @Test
    public void queryWithdrawal() {
        createAccount(1, INITIAL_AMOUNT);
        final var trackingId1 = initiateWithdrawal(1, TRANSFER_AMOUNT1);
        withdrawalCreated(1, trackingId1);

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryWithdrawalAeronRequest(trackingSequence, 1)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(List.of(new QueryWithdrawalRequest(getUuid(1))), holder.withdrawalRequests);

        process(new QueryWithdrawalSuccessEvent(getUuid(1), COMPLETED));
        assertEquals(
                new OutboundAeronMessageEnvelope(
                        SESSION_ID,
                        new WithdrawalDataAeronResponse(
                                trackingSequence,
                                TRANSFER_AMOUNT1,
                                COMPLETED
                        )
                ),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void queryWrongWithdrawal1() {
        createAccount(1, INITIAL_AMOUNT);

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryWithdrawalAeronRequest(trackingSequence, 1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoSuchEntityAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void queryWrongWithdrawal2() {
        createAccount(1, INITIAL_AMOUNT);
        initiateWithdrawal(1, TRANSFER_AMOUNT1);

        process(new InboundAeronMessageEvent(SESSION_ID, new QueryWithdrawalAeronRequest(trackingSequence, 1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoSuchEntityAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void queryWrongAccount() {
        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, 1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoSuchEntityAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void wrongTransfer1() {
        createAccount(1, INITIAL_AMOUNT);
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, 1, 2, TRANSFER_AMOUNT1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoSuchEntityAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void wrongTransfer2() {
        createAccount(1, INITIAL_AMOUNT);
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, 2, 1, TRANSFER_AMOUNT1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoSuchEntityAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void wrongTransfer3() {
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, 1, 2, TRANSFER_AMOUNT1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoSuchEntityAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void wrongTransfer4() {
        createAccount(1, INITIAL_AMOUNT);
        createAccount(2, ZERO);

        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, 1, 2, ONE.negate())));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new InvalidAmountAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void wrongTransfer5() {
        createAccount(1, INITIAL_AMOUNT);
        createAccount(2, ZERO);

        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, 1, 2, ZERO)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new InvalidAmountAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void wrongTransfer6() {
        createAccount(1, INITIAL_AMOUNT);

        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, 1, 1, TRANSFER_AMOUNT1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new SameAccountAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void wrongWithdrawal1() {
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountWithdrawAeronRequest(trackingSequence, 1, WITHDRAW_ADDRESS, TRANSFER_AMOUNT1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoSuchEntityAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void wrongWithdrawal2() {
        createAccount(1, INITIAL_AMOUNT);
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountWithdrawAeronRequest(trackingSequence, 1, WITHDRAW_ADDRESS, ONE.negate())));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new InvalidAmountAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    @Test
    public void wrongWithdrawal3() {
        createAccount(1, INITIAL_AMOUNT);
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountWithdrawAeronRequest(trackingSequence, 1, WITHDRAW_ADDRESS, ZERO)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new InvalidAmountAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope
        );
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    private void createAccount(long accountId, @NotNull BigDecimal initialAmount) {
        process(new InboundAeronMessageEvent(SESSION_ID,
                new CreateAccountAeronRequest(trackingSequence, initialAmount)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID,
                        new AccountCreatedAeronResponse(trackingSequence, accountId)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;
    }

    private void expectTransferSuccess() {
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, 1, 2, TRANSFER_AMOUNT1)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new AccountTransferDoneAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;
    }

    private void expectTransferNoFunds(@NotNull BigDecimal amount) {
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, 1, 2, amount)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID, new NoFundsAeronResponse(trackingSequence)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;
    }

    private void checkBalance(long accountId, @NotNull BigDecimal available, @NotNull BigDecimal reserved) {
        process(new InboundAeronMessageEvent(SESSION_ID, new QueryAccountAeronRequest(trackingSequence, accountId)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID,
                        new AccountDataAeronResponse(trackingSequence, available, reserved)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());
        trackingSequence++;
    }

    private long initiateWithdrawal(long accountId, @NotNull BigDecimal amount) {
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountWithdrawAeronRequest(trackingSequence, accountId, WITHDRAW_ADDRESS, amount)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(1, holder.withdrawalRequests.size());
        final var request = (CreateWithdrawalRequest) holder.withdrawalRequests.getFirst();
        assertEquals(WITHDRAW_ADDRESS, request.address().value());
        assertEquals(amount, request.amount());
        withdrawalIds.add(request.id());
        return trackingSequence++;
    }

    private long initiateTransferWithPendingWithdrawals(long fromAccountId, long toAccountId) {
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountTransferAeronRequest(trackingSequence, fromAccountId, toAccountId, TRANSFER_AMOUNT4)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(Set.of(new QueryWithdrawalRequest(withdrawalIds.get(1)),
                new QueryWithdrawalRequest(withdrawalIds.get(2))), new HashSet<>(holder.withdrawalRequests));
        return trackingSequence++;
    }

    private long initiateWithdrawalWithPendingWithdrawals(long fromAccountId) {
        process(new InboundAeronMessageEvent(SESSION_ID,
                new AccountWithdrawAeronRequest(trackingSequence, fromAccountId, WITHDRAW_ADDRESS, TRANSFER_AMOUNT4)));
        assertNull(holder.outboundAeronMessageEnvelope);
        assertEquals(Set.of(new QueryWithdrawalRequest(withdrawalIds.get(1)),
                new QueryWithdrawalRequest(withdrawalIds.get(2))), new HashSet<>(holder.withdrawalRequests));
        return trackingSequence++;
    }

    private void withdrawalCreated(long withdrawalId, long trackingId) {
        process(new CreateWithdrawalSuccessEvent(getUuid(withdrawalId)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID,
                        new AccountWithdrawalDoneAeronResponse(trackingId, withdrawalId)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    private void withdrawalDuplicated(long withdrawalId, long trackingId) {
        process(new CreateWithdrawalDuplicationFailureEvent(getUuid(withdrawalId)));
        assertEquals(
                new OutboundAeronMessageEnvelope(SESSION_ID,
                        new AccountWithdrawalDoneAeronResponse(trackingId, withdrawalId)),
                holder.outboundAeronMessageEnvelope);
        assertTrue(holder.withdrawalRequests.isEmpty());
    }

    private WithdrawalService.WithdrawalId getUuid(long withdrawalId) {
        return withdrawalIds.get((int) (withdrawalId - 1));
    }

    private void process(@NotNull Event event) {
        holder.clean();
        holder.event = event;
        controller.accept(event);
    }
}
