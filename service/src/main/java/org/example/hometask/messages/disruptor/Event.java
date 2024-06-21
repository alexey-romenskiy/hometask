package org.example.hometask.messages.disruptor;

import org.jetbrains.annotations.NotNull;

public interface Event {

    <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E;

    interface Visitor<V, E extends Throwable> {

        V visit(@NotNull InboundAeronMessageEvent event) throws E;

        V visit(@NotNull CreateWithdrawalSuccessEvent event) throws E;

        V visit(@NotNull CreateWithdrawalDuplicationFailureEvent event) throws E;

        V visit(@NotNull QueryWithdrawalSuccessEvent event) throws E;

        V visit(@NotNull QueryWithdrawalUnknownIdFailureEvent event) throws E;
    }
}
