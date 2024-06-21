package org.example.hometask.messages.response;

import org.jetbrains.annotations.NotNull;

public interface AeronResponse {

    <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E;

    interface Visitor<V, E extends Throwable> {

        V visit(@NotNull AccountCreatedAeronResponse event) throws E;

        V visit(@NotNull AccountTransferDoneAeronResponse event) throws E;

        V visit(@NotNull AccountWithdrawalDoneAeronResponse event) throws E;

        V visit(@NotNull NoFundsAeronResponse event) throws E;

        V visit(@NotNull InvalidAmountAeronResponse event) throws E;

        V visit(@NotNull NoSuchEntityAeronResponse event) throws E;

        V visit(@NotNull AccountDataAeronResponse event) throws E;

        V visit(@NotNull WithdrawalDataAeronResponse event) throws E;

        V visit(@NotNull SameAccountAeronResponse event) throws E;
    }
}
