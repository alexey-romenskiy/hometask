package org.example.hometask.state;

import org.example.hometask.disruptor.Publisher;
import org.example.hometask.messages.response.AccountDataAeronResponse;
import org.jetbrains.annotations.NotNull;

public class PendingQueryAccountOperation extends AbstractAccountPendingOperation {

    @NotNull
    private final Publisher publisher;

    private final int sessionId;

    private final long trackingId;

    public PendingQueryAccountOperation(
            @NotNull Account account,
            @NotNull Publisher publisher,
            int sessionId,
            long trackingId
    ) {
        super(account);
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
                new AccountDataAeronResponse(trackingId, account.getAvailableAmount(), account.getReservedAmount()));
    }
}
