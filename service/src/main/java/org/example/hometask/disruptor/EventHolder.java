package org.example.hometask.disruptor;

import org.example.hometask.messages.disruptor.Event;
import org.example.hometask.messages.external.WithdrawalRequest;
import org.example.hometask.messages.response.OutboundAeronMessageEnvelope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public final class EventHolder {

    @Nullable
    public Event event;

    @NotNull
    public final ArrayList<OutboundAeronMessageEnvelope> messages = new ArrayList<>(1);

    @NotNull
    public final ArrayList<WithdrawalRequest> withdrawalRequests = new ArrayList<>(1);

    public void clean() {
        event = null;
        messages.clear();
        withdrawalRequests.clear();
    }
}
