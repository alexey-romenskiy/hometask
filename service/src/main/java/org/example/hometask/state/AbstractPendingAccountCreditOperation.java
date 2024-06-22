package org.example.hometask.state;

import org.example.hometask.disruptor.Publisher;
import org.example.hometask.messages.response.NoFundsAeronResponse;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public abstract class AbstractPendingAccountCreditOperation extends AbstractAccountPendingOperation {

    @NotNull
    protected final Publisher publisher;

    protected final int sessionId;

    protected final long trackingId;

    @NotNull
    protected final BigDecimal amount;

    public AbstractPendingAccountCreditOperation(
            @NotNull Account account,
            @NotNull Publisher publisher,
            int sessionId,
            long trackingId,
            @NotNull BigDecimal amount
    ) {
        super(account);
        this.publisher = publisher;
        this.sessionId = sessionId;
        this.trackingId = trackingId;
        this.amount = amount;
    }

    public void execute() {
        if (account.credit(amount)) {
            performOperation();
        } else if (amount.compareTo(account.getPendingAmount()) > 0 || tryUpdate()) {
            respondNoFunds();
        }
    }

    @Override
    protected void nowUpdated() {
        if (account.credit(amount)) {
            performOperation();
        } else {
            respondNoFunds();
        }
    }

    private void respondNoFunds() {
        publisher.publish(sessionId, new NoFundsAeronResponse(trackingId));
    }

    protected abstract void performOperation();
}
