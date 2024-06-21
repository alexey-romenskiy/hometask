package org.example.hometask.external;

import java.io.Serial;

public class DuplicateWithdrawalIdException extends Exception {

    @Serial
    private static final long serialVersionUID = 4396170250778663821L;

    public DuplicateWithdrawalIdException(WithdrawalService.WithdrawalId id) {
        super("Withdrawal request with id[%s] is already present".formatted(id));
    }
}
