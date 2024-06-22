package org.example.hometask.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Repository {

    public final Map<Long, Account> accounts = new HashMap<>();

    public final Map<Long, Withdrawal> withdrawals = new HashMap<>();

    public final Map<UUID, Withdrawal> withdrawalsByUuid = new HashMap<>();

    private long accountSequence;

    private long withdrawalSequence;

    public long nextAccountId() {
        return ++accountSequence;
    }

    public long nextWithdrawalId() {
        return ++withdrawalSequence;
    }
}
