package org.example.hometask.state;

import org.example.hometask.disruptor.Publisher;
import org.example.hometask.messages.response.WithdrawalDataAeronResponse;
import org.jetbrains.annotations.NotNull;

public class PendingQueryWithdrawalOperation extends AbstractWithdrawalPendingOperation {

    @NotNull
    private final Publisher publisher;

    private final int sessionId;

    private final long trackingId;

    public PendingQueryWithdrawalOperation(
            @NotNull Withdrawal withdrawal,
            @NotNull Publisher publisher,
            int sessionId,
            long trackingId
    ) {
        super(withdrawal);
        this.publisher = publisher;
        this.sessionId = sessionId;
        this.trackingId = trackingId;
    }

    public void execute() {
        if (tryUpdate()) {
            nowUpdated();
        }
    }

    @Override
    protected void nowUpdated() {
        publisher.publish(sessionId,
                new WithdrawalDataAeronResponse(trackingId, withdrawal.getAmount(), withdrawal.getState()));
    }
}
