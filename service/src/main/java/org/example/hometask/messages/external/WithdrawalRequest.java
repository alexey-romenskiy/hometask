package org.example.hometask.messages.external;

import org.jetbrains.annotations.NotNull;

public interface WithdrawalRequest {

    <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E;

    interface Visitor<V, E extends Throwable> {

        V visit(@NotNull CreateWithdrawalRequest request) throws E;

        V visit(@NotNull QueryWithdrawalRequest request) throws E;
    }
}
