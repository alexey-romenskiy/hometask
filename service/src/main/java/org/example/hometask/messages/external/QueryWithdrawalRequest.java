package org.example.hometask.messages.external;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record QueryWithdrawalRequest(
        @NotNull UUID withdrawalUuid
) implements WithdrawalRequest {

    @Override
    public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
        return visitor.visit(this);
    }
}
