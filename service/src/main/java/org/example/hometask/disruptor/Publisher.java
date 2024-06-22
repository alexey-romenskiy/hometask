package org.example.hometask.disruptor;

import org.example.hometask.messages.external.WithdrawalRequest;
import org.example.hometask.messages.response.AeronResponse;
import org.example.hometask.messages.response.OutboundAeronMessageEnvelope;
import org.jetbrains.annotations.NotNull;

public class Publisher {

    @NotNull
    private final EventContext eventContext;

    public Publisher(@NotNull EventContext eventContext) {
        this.eventContext = eventContext;
    }

    public void publish(int sessionId, @NotNull AeronResponse message) {
        eventContext.holder().messages.add(new OutboundAeronMessageEnvelope(sessionId, message));
    }

    public void publish(@NotNull WithdrawalRequest request) {
        eventContext.holder().withdrawalRequests.add(request);
    }
}
