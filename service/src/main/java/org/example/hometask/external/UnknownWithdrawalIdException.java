package org.example.hometask.external;

import java.io.Serial;

public class UnknownWithdrawalIdException extends Exception {

    @Serial
    private static final long serialVersionUID = 4396170250778663821L;

    public UnknownWithdrawalIdException(WithdrawalService.WithdrawalId id) {
        super("Request %s is not found".formatted(id));
    }
}
