package org.example.hometask.state;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static java.math.BigDecimal.ZERO;

public class Account {

    private final long id;

    /**
     * All acoount's withdrawals requiring periodically polling of the state.
     */
    private final Map<Long, Withdrawal> pendingWithdrawals = new HashMap<>();

    @NotNull
    private BigDecimal availableAmount;

    /**
     * Contains sum of amounts of all uncompleted withdrawals.
     */
    @NotNull
    private BigDecimal reservedAmount = ZERO;

    /**
     * Contains sum of amounts of all pending withdrawals.
     */
    @NotNull
    private BigDecimal pendingAmount = ZERO;

    public Account(long id, @NotNull BigDecimal availableAmount) {
        this.id = id;
        this.availableAmount = availableAmount;
    }

    public long getId() {
        return id;
    }

    @NotNull
    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    @NotNull
    public BigDecimal getReservedAmount() {
        return reservedAmount;
    }

    /**
     * @return The largest possible amount available if all pending withdrawals will fail.
     */
    @NotNull
    public BigDecimal getPendingAmount() {
        return availableAmount.add(pendingAmount);
    }

    public Map<Long, Withdrawal> getPendingWithdrawals() {
        return pendingWithdrawals;
    }

    public boolean reduce(@NotNull BigDecimal amount) {
        if (amount.compareTo(availableAmount) > 0) {
            return false;
        }
        availableAmount = availableAmount.subtract(amount);
        return true;
    }

    public void adjustAvailable(@NotNull BigDecimal amount) {
        availableAmount = availableAmount.add(amount);
    }

    public void adjustReserved(@NotNull BigDecimal amount) {
        reservedAmount = reservedAmount.add(amount);
    }

    public void withdrawalCreated(@NotNull Withdrawal withdrawal) {
        pendingAmount = pendingAmount.add(withdrawal.getAmount());
        pendingWithdrawals.put(withdrawal.getId(), withdrawal);
    }

    public void withdrawalCompleted(@NotNull Withdrawal withdrawal) {
        pendingAmount = pendingAmount.subtract(withdrawal.getAmount());
        pendingWithdrawals.remove(withdrawal.getId(), withdrawal);
        switch (withdrawal.getState()) {
            case COMPLETED -> adjustReserved(withdrawal.getAmount().negate());
            case FAILED -> {
                adjustReserved(withdrawal.getAmount().negate());
                adjustAvailable(withdrawal.getAmount());
            }
            default -> throw new IllegalStateException();
        }
    }
}
