package org.example.hometask.external;

import org.example.hometask.messages.WithdrawalState;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.example.hometask.messages.WithdrawalState.FAILED;
import static org.example.hometask.messages.WithdrawalState.PROCESSING;

public class WithdrawalServiceStub implements WithdrawalService {

    private final ConcurrentMap<WithdrawalId, Withdrawal> requests = new ConcurrentHashMap<>();

    @Override
    public void requestWithdrawal(
            WithdrawalId id,
            Address address,
            BigDecimal amount
    ) throws DuplicateWithdrawalIdException {

        final var existing = requests.putIfAbsent(id, new Withdrawal(finalState(), finaliseAt(), address, amount));
        if (existing != null && !Objects.equals(existing.address, address) &&
            !Objects.equals(existing.amount, amount)) {
            throw new DuplicateWithdrawalIdException(id);
        }
    }

    private WithdrawalState finalState() {
        return ThreadLocalRandom.current().nextBoolean() ? WithdrawalState.COMPLETED : FAILED;
    }

    private long finaliseAt() {
        return System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(1000, 10000);
    }

    @Override
    public WithdrawalState getRequestState(WithdrawalId id) throws UnknownWithdrawalIdException {
        final var request = requests.get(id);
        if (request == null) {
            throw new UnknownWithdrawalIdException(id);
        }
        return request.finalState();
    }

    record Withdrawal(WithdrawalState state, long finaliseAt, Address address, BigDecimal amount) {

        public WithdrawalState finalState() {
            return finaliseAt <= System.currentTimeMillis() ? state : PROCESSING;
        }
    }
}
