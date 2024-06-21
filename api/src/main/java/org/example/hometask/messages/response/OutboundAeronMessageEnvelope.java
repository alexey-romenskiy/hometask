package org.example.hometask.messages.response;

import org.jetbrains.annotations.NotNull;

public record OutboundAeronMessageEnvelope(
        int sessionId,
        @NotNull AeronResponse message
) {
    // empty
}
