package org.example.hometask.state;

import org.example.hometask.disruptor.Publisher;
import org.example.hometask.messages.response.AccountTransferDoneAeronResponse;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public class PendingTransferOperation extends AbstractPendingAccountCreditOperation {

    @NotNull
    private final Account toAccount;

    public PendingTransferOperation(
            @NotNull Account account,
            @NotNull Publisher publisher,
            int sessionId,
            long trackingId,
            @NotNull BigDecimal amount,
            @NotNull Account toAccount
    ) {
        super(account, publisher, sessionId, trackingId, amount);
        this.toAccount = toAccount;
    }

    @Override
    protected void performOperation() {
        toAccount.adjustAvailable(amount);
        publisher.publish(sessionId, new AccountTransferDoneAeronResponse(trackingId));
    }
}
