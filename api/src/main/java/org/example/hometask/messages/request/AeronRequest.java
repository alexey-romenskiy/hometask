package org.example.hometask.messages.request;

import org.jetbrains.annotations.NotNull;

public interface AeronRequest {

    <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E;

    interface Visitor<V, E extends Throwable> {

        V visit(@NotNull CreateAccountAeronRequest message) throws E;

        V visit(@NotNull QueryAccountAeronRequest message) throws E;

        V visit(@NotNull AccountTransferAeronRequest message) throws E;

        V visit(@NotNull AccountWithdrawAeronRequest message) throws E;

        V visit(@NotNull QueryWithdrawalAeronRequest message) throws E;
    }
}
