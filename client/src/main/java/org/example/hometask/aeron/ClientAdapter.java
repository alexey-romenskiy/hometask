package org.example.hometask.aeron;

import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.example.hometask.api.AccountCreatedDecoder;
import org.example.hometask.api.AccountDataDecoder;
import org.example.hometask.api.AccountTransferDoneDecoder;
import org.example.hometask.api.AccountWithdrawalDoneDecoder;
import org.example.hometask.api.InvalidAmountDecoder;
import org.example.hometask.api.MessageHeaderDecoder;
import org.example.hometask.api.NoFundsDecoder;
import org.example.hometask.api.NoSuchEntityDecoder;
import org.example.hometask.api.SameAccountDecoder;
import org.example.hometask.api.WithdrawalDataDecoder;
import org.example.hometask.messages.WithdrawalState;
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

import java.util.concurrent.BlockingQueue;

import static org.example.hometask.utils.Utils.getBigDecimal;

public class ClientAdapter implements FragmentHandler {

    private final AccountCreatedDecoder accountCreatedDecoder = new AccountCreatedDecoder();
    private final AccountTransferDoneDecoder accountTransferDoneDecoder = new AccountTransferDoneDecoder();
    private final AccountWithdrawalDoneDecoder accountWithdrawalDoneDecoder = new AccountWithdrawalDoneDecoder();
    private final NoFundsDecoder noFundsDecoder = new NoFundsDecoder();
    private final InvalidAmountDecoder invalidAmountDecoder = new InvalidAmountDecoder();
    private final NoSuchEntityDecoder noSuchEntityDecoder = new NoSuchEntityDecoder();
    private final AccountDataDecoder accountDataDecoder = new AccountDataDecoder();
    private final WithdrawalDataDecoder withdrawalDataDecoder = new WithdrawalDataDecoder();
    private final SameAccountDecoder sameAccountDecoder = new SameAccountDecoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final BlockingQueue<AeronResponse> responseQueue;

    public ClientAdapter(@NotNull BlockingQueue<AeronResponse> responseQueue) {
        this.responseQueue = responseQueue;
    }

    @Override
    public void onFragment(@NotNull DirectBuffer buffer, int offset, int length, @NotNull Header header) {

        headerDecoder.wrap(buffer, offset);
        //noinspection ResultOfMethodCallIgnored
        responseQueue.offer(parse(buffer, offset));
    }

    @NotNull
    private AeronResponse parse(@NotNull DirectBuffer buffer, int offset) {
        switch (headerDecoder.templateId()) {
            case AccountCreatedDecoder.TEMPLATE_ID -> {
                accountCreatedDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(),
                        headerDecoder.version());
                return new AccountCreatedAeronResponse(accountCreatedDecoder.trackingId(),
                        accountCreatedDecoder.accountId());
            }
            case AccountTransferDoneDecoder.TEMPLATE_ID -> {
                accountTransferDoneDecoder.wrap(buffer, offset + headerDecoder.encodedLength(),
                        headerDecoder.blockLength(), headerDecoder.version());
                return new AccountTransferDoneAeronResponse(accountTransferDoneDecoder.trackingId());
            }
            case AccountWithdrawalDoneDecoder.TEMPLATE_ID -> {
                accountWithdrawalDoneDecoder.wrap(buffer, offset + headerDecoder.encodedLength(),
                        headerDecoder.blockLength(), headerDecoder.version());
                return new AccountWithdrawalDoneAeronResponse(accountWithdrawalDoneDecoder.trackingId(),
                        accountWithdrawalDoneDecoder.withdrawalId());
            }
            case NoFundsDecoder.TEMPLATE_ID -> {
                noFundsDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(),
                        headerDecoder.version());
                return new NoFundsAeronResponse(noFundsDecoder.trackingId());
            }
            case InvalidAmountDecoder.TEMPLATE_ID -> {
                invalidAmountDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(),
                        headerDecoder.version());
                return new InvalidAmountAeronResponse(invalidAmountDecoder.trackingId());
            }
            case NoSuchEntityDecoder.TEMPLATE_ID -> {
                noSuchEntityDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(),
                        headerDecoder.version());
                return new NoSuchEntityAeronResponse(noSuchEntityDecoder.trackingId());
            }
            case AccountDataDecoder.TEMPLATE_ID -> {
                accountDataDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(),
                        headerDecoder.version());
                return new AccountDataAeronResponse(accountDataDecoder.trackingId(),
                        getBigDecimal(accountDataDecoder.availableAmount()),
                        getBigDecimal(accountDataDecoder.reservedAmount()));
            }
            case WithdrawalDataDecoder.TEMPLATE_ID -> {
                withdrawalDataDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(),
                        headerDecoder.version());
                return new WithdrawalDataAeronResponse(
                        withdrawalDataDecoder.trackingId(),
                        getBigDecimal(withdrawalDataDecoder.amount()),
                        switch (withdrawalDataDecoder.state()) {
                            case PROCESSING -> WithdrawalState.PROCESSING;
                            case COMPLETED -> WithdrawalState.COMPLETED;
                            case FAILED -> WithdrawalState.FAILED;
                            case NULL_VAL -> throw new IllegalArgumentException("Null state is not allowed");
                        }
                );
            }
            case SameAccountDecoder.TEMPLATE_ID -> {
                sameAccountDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(),
                        headerDecoder.version());
                return new SameAccountAeronResponse(sameAccountDecoder.trackingId());
            }
            default -> throw new RuntimeException("Unknown message");
        }
    }
}
