package org.example.hometask.aeron;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.Agent;
import org.example.hometask.ClientConstants;
import org.example.hometask.api.AccountTransferEncoder;
import org.example.hometask.api.AccountWithdrawEncoder;
import org.example.hometask.api.CreateAccountEncoder;
import org.example.hometask.api.MessageHeaderEncoder;
import org.example.hometask.api.QueryAccountEncoder;
import org.example.hometask.api.QueryWithdrawalEncoder;
import org.example.hometask.api.RpcConnectRequestEncoder;
import org.example.hometask.messages.request.AccountTransferAeronRequest;
import org.example.hometask.messages.request.AccountWithdrawAeronRequest;
import org.example.hometask.messages.request.AeronRequest;
import org.example.hometask.messages.request.CreateAccountAeronRequest;
import org.example.hometask.messages.request.QueryAccountAeronRequest;
import org.example.hometask.messages.request.QueryWithdrawalAeronRequest;
import org.example.hometask.messages.response.AeronResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.agrona.CloseHelper.quietClose;
import static org.example.hometask.utils.Utils.setBigDecimal;

public class ClientAgent implements Agent {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final Aeron aeron;
    private final ConcurrentLinkedQueue<AeronRequest> requestQueue;
    private final BlockingQueue<AeronResponse> responseQueue;
    private final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(250);
    private final ClientAdapter clientAdapter;
    private final RpcConnectRequestEncoder connectRequest = new RpcConnectRequestEncoder();
    private final CreateAccountEncoder createAccountEncoder = new CreateAccountEncoder();
    private final QueryAccountEncoder queryAccountEncoder = new QueryAccountEncoder();
    private final AccountTransferEncoder accountTransferEncoder = new AccountTransferEncoder();
    private final AccountWithdrawEncoder accountWithdrawEncoder = new AccountWithdrawEncoder();
    private final QueryWithdrawalEncoder queryWithdrawalEncoder = new QueryWithdrawalEncoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    private State state;
    private ExclusivePublication publication;
    private Subscription subscription;

    public ClientAgent(
            @NotNull Aeron aeron,
            @NotNull ConcurrentLinkedQueue<AeronRequest> requestQueue,
            @NotNull BlockingQueue<AeronResponse> responseQueue
    ) {
        this.aeron = aeron;
        this.requestQueue = requestQueue;
        this.responseQueue = responseQueue;
        this.clientAdapter = new ClientAdapter(responseQueue);
    }

    @Override
    public void onStart() {
        logger.info("Client starting");
        state = State.AWAITING_OUTBOUND_CONNECT;
        publication = aeron.addExclusivePublication(ClientConstants.SERVER_URI, ClientConstants.RPC_STREAM);
        subscription = aeron.addSubscription(ClientConstants.CLIENT_URI, ClientConstants.RPC_STREAM);
    }

    @Override
    public int doWork() {
        switch (state) {
            case AWAITING_OUTBOUND_CONNECT:
                awaitConnected();
                state = State.CONNECTED;
                break;
            case CONNECTED:
                sendConnectRequest();
                state = State.AWAITING_INBOUND_CONNECT;
                break;
            case AWAITING_INBOUND_CONNECT:
                awaitSubscriptionConnected();
                state = State.READY;
                break;
            case READY:
                sendMessage();
                subscription.poll(clientAdapter, 1);
                break;
            default:
                break;
        }
        return 0;
    }

    private void sendMessage() {

        final var request = requestQueue.poll();
        if (request == null) {
            return;
        }

        final int length = request.accept(new AeronRequest.Visitor<Integer, RuntimeException>() {
            @Override
            public Integer visit(@NotNull CreateAccountAeronRequest message) {
                createAccountEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                createAccountEncoder.trackingId(message.trackingId());
                setBigDecimal(createAccountEncoder.initialAmount(), message.initialAmount());
                return createAccountEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull QueryAccountAeronRequest message) {
                queryAccountEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                queryAccountEncoder.trackingId(message.trackingId());
                queryAccountEncoder.accountId(message.accountId());
                return queryAccountEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull AccountTransferAeronRequest message) {
                accountTransferEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                accountTransferEncoder.trackingId(message.trackingId());
                accountTransferEncoder.fromAccountId(message.fromAccountId());
                accountTransferEncoder.toAccountId(message.toAccountId());
                setBigDecimal(accountTransferEncoder.amount(), message.amount());
                return accountTransferEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull AccountWithdrawAeronRequest message) {
                accountWithdrawEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                accountWithdrawEncoder.trackingId(message.trackingId());
                accountWithdrawEncoder.fromAccountId(message.fromAccountId());
                accountWithdrawEncoder.toAddress(message.toAddress());
                setBigDecimal(accountWithdrawEncoder.amount(), message.amount());
                return accountWithdrawEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull QueryWithdrawalAeronRequest message) {
                queryWithdrawalEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                queryWithdrawalEncoder.trackingId(message.trackingId());
                queryWithdrawalEncoder.withdrawalId(message.withdrawalId());
                return queryWithdrawalEncoder.encodedLength();
            }
        });

        send(buffer, headerEncoder.encodedLength() + length);
    }

    private void sendConnectRequest() {

        connectRequest.wrapAndApplyHeader(buffer, 0, headerEncoder);
        connectRequest.returnConnectStream(ClientConstants.RPC_STREAM);
        connectRequest.returnConnectUri(ClientConstants.CLIENT_URI);

        send(buffer, headerEncoder.encodedLength() + connectRequest.encodedLength());
    }

    private void awaitSubscriptionConnected() {
        logger.info("awaiting inbound server connect");

        while (!subscription.isConnected()) {
            aeron.context().idleStrategy().idle();
        }
    }

    private void awaitConnected() {

        logger.info("awaiting outbound server connect");

        while (!publication.isConnected()) {
            aeron.context().idleStrategy().idle();
        }
    }

    @Override
    public void onClose() {
        quietClose(publication);
        quietClose(subscription);
    }

    @Override
    public String roleName() {
        return "client";
    }

    private void send(final DirectBuffer buffer, final int length) {
        int retries = 3;

        do {
            //in this example, the offset it always zero. This will not always be the case.
            final long result = publication.offer(buffer, 0, length);
            if (result > 0) {
                break;
            } else {
                logger.info("aeron returned {} on offer", result);
            }
        }
        while (--retries > 0);
    }

    enum State {
        AWAITING_OUTBOUND_CONNECT,
        CONNECTED,
        READY,
        AWAITING_INBOUND_CONNECT
    }
}
