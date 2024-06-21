package org.example.hometask.disruptor;

import com.lmax.disruptor.RingBuffer;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.example.hometask.api.AccountTransferDecoder;
import org.example.hometask.api.AccountWithdrawDecoder;
import org.example.hometask.api.CreateAccountDecoder;
import org.example.hometask.api.MessageHeaderDecoder;
import org.example.hometask.api.QueryAccountDecoder;
import org.example.hometask.api.QueryWithdrawalDecoder;
import org.example.hometask.api.RpcConnectRequestDecoder;
import org.example.hometask.messages.disruptor.InboundAeronMessageEvent;
import org.example.hometask.messages.request.AccountTransferAeronRequest;
import org.example.hometask.messages.request.AccountWithdrawAeronRequest;
import org.example.hometask.messages.request.AeronRequest;
import org.example.hometask.messages.request.CreateAccountAeronRequest;
import org.example.hometask.messages.request.QueryAccountAeronRequest;
import org.example.hometask.messages.request.QueryWithdrawalAeronRequest;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

import static org.example.hometask.utils.Utils.getBigDecimal;

public class ServerAdapter implements FragmentHandler {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final Aeron aeron;

    @NotNull
    private final RingBuffer<EventHolder> ringBuffer;

    @NotNull
    private final ConcurrentHashMap<Integer, Publication> publicationsBySessionId;

    private final RpcConnectRequestDecoder connectRequest = new RpcConnectRequestDecoder();
    private final CreateAccountDecoder createAccountDecoder = new CreateAccountDecoder();
    private final QueryAccountDecoder queryAccountDecoder = new QueryAccountDecoder();
    private final AccountTransferDecoder accountTransferDecoder = new AccountTransferDecoder();
    private final AccountWithdrawDecoder accountWithdrawDecoder = new AccountWithdrawDecoder();
    private final QueryWithdrawalDecoder queryWithdrawalDecoder = new QueryWithdrawalDecoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    public ServerAdapter(
            @NotNull Aeron aeron,
            @NotNull RingBuffer<EventHolder> ringBuffer,
            @NotNull ConcurrentHashMap<Integer, Publication> publicationsBySessionId
    ) {
        this.aeron = aeron;
        this.ringBuffer = ringBuffer;
        this.publicationsBySessionId = publicationsBySessionId;
    }

    @Override
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header) {

        headerDecoder.wrap(buffer, offset);
        final int headerLength = headerDecoder.encodedLength();
        final int actingLength = headerDecoder.blockLength();
        final int actingVersion = headerDecoder.version();

        switch (headerDecoder.templateId()) {
            case RpcConnectRequestDecoder.TEMPLATE_ID:
                connectRequest.wrap(buffer, offset + headerLength, actingLength, actingVersion);
                final var streamId = connectRequest.returnConnectStream();
                final var uri = connectRequest.returnConnectUri();
                blockingOpenConnection(streamId, uri, header.sessionId());
                break;
            case CreateAccountDecoder.TEMPLATE_ID:
                createAccountDecoder.wrap(buffer, offset + headerLength, actingLength, actingVersion);
                publish(header.sessionId(), new CreateAccountAeronRequest(createAccountDecoder.trackingId(),
                        getBigDecimal(createAccountDecoder.initialAmount())));
                break;
            case QueryAccountDecoder.TEMPLATE_ID:
                queryAccountDecoder.wrap(buffer, offset + headerLength, actingLength, actingVersion);
                publish(header.sessionId(), new QueryAccountAeronRequest(queryAccountDecoder.trackingId(),
                        queryAccountDecoder.accountId()));
                break;
            case AccountTransferDecoder.TEMPLATE_ID:
                accountTransferDecoder.wrap(buffer, offset + headerLength, actingLength, actingVersion);
                publish(header.sessionId(), new AccountTransferAeronRequest(accountTransferDecoder.trackingId(),
                        accountTransferDecoder.fromAccountId(), accountTransferDecoder.toAccountId(),
                        getBigDecimal(accountTransferDecoder.amount())));
                break;
            case AccountWithdrawDecoder.TEMPLATE_ID:
                accountWithdrawDecoder.wrap(buffer, offset + headerLength, actingLength, actingVersion);
                publish(header.sessionId(), new AccountWithdrawAeronRequest(accountWithdrawDecoder.trackingId(),
                        accountWithdrawDecoder.fromAccountId(), accountWithdrawDecoder.toAddress(),
                        getBigDecimal(accountWithdrawDecoder.amount())));
                break;
            case QueryWithdrawalDecoder.TEMPLATE_ID:
                queryWithdrawalDecoder.wrap(buffer, offset + headerLength, actingLength, actingVersion);
                publish(header.sessionId(), new QueryWithdrawalAeronRequest(queryWithdrawalDecoder.trackingId(),
                        queryWithdrawalDecoder.withdrawalId()));
                break;
            default:
                break;
        }
    }

    private void publish(int sessionId, @NotNull AeronRequest message) {
        final long sequence = ringBuffer.next();
        try {
            final var holder = ringBuffer.get(sequence);
            holder.event = new InboundAeronMessageEvent(sessionId, message);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    private void blockingOpenConnection(final int streamId, final String uri, int sessionId) {
        logger.info("Received connect request with response URI {} stream {}", uri, streamId);
        final var publication = aeron.addExclusivePublication(uri, streamId);
        publicationsBySessionId.put(sessionId, publication);
    }
}
