package org.example.hometask;

import org.example.hometask.aeron.Client;
import org.example.hometask.messages.request.AccountTransferAeronRequest;
import org.example.hometask.messages.request.AccountWithdrawAeronRequest;
import org.example.hometask.messages.request.CreateAccountAeronRequest;
import org.example.hometask.messages.request.QueryAccountAeronRequest;
import org.example.hometask.messages.request.QueryWithdrawalAeronRequest;
import org.example.hometask.messages.response.AccountCreatedAeronResponse;
import org.example.hometask.messages.response.AccountDataAeronResponse;
import org.example.hometask.messages.response.AccountTransferDoneAeronResponse;
import org.example.hometask.messages.response.AccountWithdrawalDoneAeronResponse;
import org.example.hometask.messages.response.InvalidAmountAeronResponse;
import org.example.hometask.messages.response.NoFundsAeronResponse;
import org.example.hometask.messages.response.NoSuchEntityAeronResponse;
import org.example.hometask.messages.response.SameAccountAeronResponse;
import org.example.hometask.messages.response.WithdrawalDataAeronResponse;
import org.junit.Test;

import java.math.BigDecimal;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ServiceIT {

    @Test
    public void transfer() throws InterruptedException {

        final var client = new Client();

        client.send(new CreateAccountAeronRequest(1, ONE.negate()));
        client.send(new QueryAccountAeronRequest(2, 1));
        client.send(new QueryWithdrawalAeronRequest(3, 1));
        client.send(new AccountTransferAeronRequest(4, 1, 2, ONE));
        client.send(new AccountWithdrawAeronRequest(5, 1, "foobar", ONE));

        assertEquals(new InvalidAmountAeronResponse(1), client.responses().take());
        assertEquals(new NoSuchEntityAeronResponse(2), client.responses().take());
        assertEquals(new NoSuchEntityAeronResponse(3), client.responses().take());
        assertEquals(new NoSuchEntityAeronResponse(4), client.responses().take());
        assertEquals(new NoSuchEntityAeronResponse(5), client.responses().take());

        client.send(new CreateAccountAeronRequest(6, new BigDecimal("123")));
        client.send(new CreateAccountAeronRequest(7, ZERO));
        assertEquals(new AccountCreatedAeronResponse(6, 1), client.responses().take());
        assertEquals(new AccountCreatedAeronResponse(7, 2), client.responses().take());

        client.send(new AccountTransferAeronRequest(8, 1, 2, ONE.negate()));
        client.send(new AccountTransferAeronRequest(9, 1, 2, ZERO));
        assertEquals(new InvalidAmountAeronResponse(8), client.responses().take());
        assertEquals(new InvalidAmountAeronResponse(9), client.responses().take());

        client.send(new AccountWithdrawAeronRequest(10, 1, "foobar", ONE.negate()));
        client.send(new AccountWithdrawAeronRequest(11, 1, "foobar", ZERO));
        assertEquals(new InvalidAmountAeronResponse(10), client.responses().take());
        assertEquals(new InvalidAmountAeronResponse(11), client.responses().take());

        client.send(new AccountTransferAeronRequest(12, 1, 2, ONE));
        assertEquals(new AccountTransferDoneAeronResponse(12), client.responses().take());

        client.send(new QueryAccountAeronRequest(13, 1));
        assertEquals(new AccountDataAeronResponse(13, new BigDecimal("122"), ZERO), client.responses().take());

        client.send(new AccountWithdrawAeronRequest(14, 1, "foobar", ONE));
        assertEquals(new AccountWithdrawalDoneAeronResponse(14, 1), client.responses().take());

        client.send(new QueryWithdrawalAeronRequest(15, 1));
        final var withdrawalDataAeronResponse = (WithdrawalDataAeronResponse) client.responses().take();
        assertEquals(15, withdrawalDataAeronResponse.trackingId());
        assertEquals(ONE, withdrawalDataAeronResponse.amount());
        assertNotNull(withdrawalDataAeronResponse.state());

        client.send(new AccountWithdrawAeronRequest(16, 1, "foobar", new BigDecimal("999")));
        client.send(new AccountTransferAeronRequest(17, 1, 2, new BigDecimal("999")));
        assertEquals(new NoFundsAeronResponse(16), client.responses().take());
        assertEquals(new NoFundsAeronResponse(17), client.responses().take());

        client.send(new AccountTransferAeronRequest(18, 1, 1, ONE));
        assertEquals(new SameAccountAeronResponse(18), client.responses().take());

        assertNull(client.responses().poll(1, SECONDS));
        client.shutdown();
    }
}
