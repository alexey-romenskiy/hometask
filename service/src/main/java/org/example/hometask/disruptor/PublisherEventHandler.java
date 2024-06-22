package org.example.hometask.disruptor;

import com.lmax.disruptor.EventHandler;
import io.aeron.Publication;
import org.agrona.ExpandableDirectByteBuffer;
import org.example.hometask.api.AccountCreatedEncoder;
import org.example.hometask.api.AccountDataEncoder;
import org.example.hometask.api.AccountTransferDoneEncoder;
import org.example.hometask.api.AccountWithdrawalDoneEncoder;
import org.example.hometask.api.InvalidAmountEncoder;
import org.example.hometask.api.MessageHeaderEncoder;
import org.example.hometask.api.NoFundsEncoder;
import org.example.hometask.api.NoSuchEntityEncoder;
import org.example.hometask.api.SameAccountEncoder;
import org.example.hometask.api.WithdrawalDataEncoder;
import org.example.hometask.api.WithdrawalState;
import org.example.hometask.messages.response.AccountCreatedAeronResponse;
import org.example.hometask.messages.response.AccountDataAeronResponse;
import org.example.hometask.messages.response.AccountTransferDoneAeronResponse;
import org.example.hometask.messages.response.AccountWithdrawalDoneAeronResponse;
import org.example.hometask.messages.response.AeronResponse;
import org.example.hometask.messages.response.InvalidAmountAeronResponse;
import org.example.hometask.messages.response.NoFundsAeronResponse;
import org.example.hometask.messages.response.NoSuchEntityAeronResponse;
import org.example.hometask.messages.response.SameAccountAeronResponse;
import org.example.hometask.messages.response.WithdrawalDataAeronResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

import static org.example.hometask.utils.Utils.setBigDecimal;

class PublisherEventHandler implements EventHandler<EventHolder> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final ConcurrentHashMap<Integer, Publication> publicationsBySessionId;

    private final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(512);
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AccountCreatedEncoder accountCreatedEncoder = new AccountCreatedEncoder();
    private final AccountTransferDoneEncoder accountTransferDoneEncoder = new AccountTransferDoneEncoder();
    private final AccountWithdrawalDoneEncoder accountWithdrawalDoneEncoder = new AccountWithdrawalDoneEncoder();
    private final NoFundsEncoder noFundsEncoder = new NoFundsEncoder();
    private final InvalidAmountEncoder invalidAmountEncoder = new InvalidAmountEncoder();
    private final NoSuchEntityEncoder noSuchEntityEncoder = new NoSuchEntityEncoder();
    private final AccountDataEncoder accountDataEncoder = new AccountDataEncoder();
    private final WithdrawalDataEncoder withdrawalDataEncoder = new WithdrawalDataEncoder();
    private final SameAccountEncoder sameAccountEncoder = new SameAccountEncoder();

    private volatile boolean shutdown;

    public PublisherEventHandler(@NotNull ConcurrentHashMap<Integer, Publication> publicationsBySessionId) {
        this.publicationsBySessionId = publicationsBySessionId;
    }

    @Override
    public void onEvent(@NotNull EventHolder holder, long sequence, boolean endOfBatch) {

        for (final var envelope : holder.messages) {
            publish(envelope.sessionId(), serialize(envelope.message()));
        }
    }

    public void shutdown() {
        shutdown = true;
    }

    private void publish(int sessionId, int length) {

        final var publication = publicationsBySessionId.get(sessionId);
        if (publication == null) {
            return;
        }

        // we decided not to limit max attempts to avoid skipping some messages some randomly
        while (true) {

            final long result = publication.offer(buffer, 0, headerEncoder.encodedLength() + length);
            if (result >= 0) {
                break;
            }

            logger.warn("Publication returned: {}", result);

            if (shutdown) {
                // otherwise it may spin indefinitely
                break;
            }
        }
    }

    private int serialize(@NotNull AeronResponse message) {

        return message.accept(new AeronResponse.Visitor<Integer, RuntimeException>() {
            @Override
            public Integer visit(@NotNull AccountCreatedAeronResponse event) {
                accountCreatedEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                accountCreatedEncoder.trackingId(event.trackingId());
                accountCreatedEncoder.accountId(event.accountId());
                return accountCreatedEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull AccountTransferDoneAeronResponse event) {
                accountTransferDoneEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                accountTransferDoneEncoder.trackingId(event.trackingId());
                return accountTransferDoneEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull AccountWithdrawalDoneAeronResponse event) {
                accountWithdrawalDoneEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                accountWithdrawalDoneEncoder.trackingId(event.trackingId());
                accountWithdrawalDoneEncoder.withdrawalId(event.withdrawalId());
                return accountWithdrawalDoneEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull NoFundsAeronResponse event) {
                noFundsEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                noFundsEncoder.trackingId(event.trackingId());
                return noFundsEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull InvalidAmountAeronResponse event) {
                invalidAmountEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                invalidAmountEncoder.trackingId(event.trackingId());
                return invalidAmountEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull NoSuchEntityAeronResponse event) {
                noSuchEntityEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                noSuchEntityEncoder.trackingId(event.trackingId());
                return noSuchEntityEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull AccountDataAeronResponse event) {
                accountDataEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                accountDataEncoder.trackingId(event.trackingId());
                setBigDecimal(accountDataEncoder.availableAmount(), event.availableAmount());
                setBigDecimal(accountDataEncoder.reservedAmount(), event.reservedAmount());
                return accountDataEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull WithdrawalDataAeronResponse event) {
                withdrawalDataEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                withdrawalDataEncoder.trackingId(event.trackingId());
                setBigDecimal(withdrawalDataEncoder.amount(), event.amount());
                withdrawalDataEncoder.state(switch (event.state()) {
                    case PROCESSING -> WithdrawalState.PROCESSING;
                    case COMPLETED -> WithdrawalState.COMPLETED;
                    case FAILED -> WithdrawalState.FAILED;
                });
                return withdrawalDataEncoder.encodedLength();
            }

            @Override
            public Integer visit(@NotNull SameAccountAeronResponse event) throws RuntimeException {
                sameAccountEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
                sameAccountEncoder.trackingId(event.trackingId());
                return sameAccountEncoder.encodedLength();
            }
        });
    }
}
