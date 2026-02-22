package pl.htgmc.htgeconomy.dolary;

import pl.htgmc.htgeconomy.history.Action;
import pl.htgmc.htgeconomy.history.Currency;
import pl.htgmc.htgeconomy.history.EconomyHistoryStore;
import pl.htgmc.htgeconomy.history.EconomyTxn;
import pl.htgmc.htgeconomy.storage.JsonBalanceStorage;

import java.util.UUID;

public final class DolaryService {

    private final JsonBalanceStorage storage;
    private final EconomyHistoryStore history; // może być null

    public DolaryService(JsonBalanceStorage storage) {
        this(storage, null);
    }

    public DolaryService(JsonBalanceStorage storage, EconomyHistoryStore history) {
        this.storage = storage;
        this.history = history;
    }

    public long get(UUID uuid) {
        return storage.get(uuid);
    }

    // --- stare metody (kompatybilność)

    public void set(UUID uuid, long bal) {
        set(uuid, bal, null, null);
    }

    public void add(UUID uuid, long amount) {
        add(uuid, amount, null, null);
    }

    public boolean take(UUID uuid, long amount) {
        return take(uuid, amount, null, null);
    }

    public boolean pay(UUID from, UUID to, long amount) {
        return pay(from, to, amount, null, null);
    }

    // --- nowe metody (z historią)

    public void set(UUID uuid, long bal, UUID actor, String reason) {
        if (uuid == null) return;

        long target = Math.max(0L, bal);
        long before = Math.max(0L, storage.get(uuid));
        long after = target;

        storage.set(uuid, target);
        storage.saveAsync();

        log(new EconomyTxn(
                System.currentTimeMillis(),
                Currency.DOLARY,
                Action.SET,
                uuid,
                null,
                target,
                before,
                after,
                actor,
                reason
        ));
    }

    public void add(UUID uuid, long amount, UUID actor, String reason) {
        if (uuid == null) return;
        if (amount <= 0) return;

        long before = Math.max(0L, storage.get(uuid));
        long after = before + amount; // pewne, nie zależy od storage.get() po operacji

        storage.add(uuid, amount);
        storage.saveAsync();

        log(new EconomyTxn(
                System.currentTimeMillis(),
                Currency.DOLARY,
                Action.ADD,
                uuid,
                null,
                amount,
                before,
                after,
                actor,
                reason
        ));
    }

    public boolean take(UUID uuid, long amount, UUID actor, String reason) {
        if (uuid == null) return false;
        if (amount <= 0) return true;

        long before = Math.max(0L, storage.get(uuid));
        if (before < amount) return false;

        long after = before - amount;

        boolean ok = storage.take(uuid, amount);
        storage.saveAsync();

        if (ok) {
            log(new EconomyTxn(
                    System.currentTimeMillis(),
                    Currency.DOLARY,
                    Action.TAKE,
                    uuid,
                    null,
                    amount,
                    before,
                    after,
                    actor,
                    reason
            ));
        }

        return ok;
    }

    public boolean pay(UUID from, UUID to, long amount, UUID actor, String reason) {
        if (from == null || to == null) return false;
        if (amount <= 0) return true;

        long beforeFrom = Math.max(0L, storage.get(from));
        if (beforeFrom < amount) return false;

        long beforeTo = Math.max(0L, storage.get(to));

        long afterFrom = beforeFrom - amount;
        long afterTo = beforeTo + amount;

        boolean ok = storage.transfer(from, to, amount);
        storage.saveAsync();

        if (ok) {
            long ts = System.currentTimeMillis();

            // wpis dla ODBIORCY
            log(new EconomyTxn(
                    ts,
                    Currency.DOLARY,
                    Action.PAY,
                    to,
                    from,
                    amount,
                    beforeTo,
                    afterTo,
                    actor,
                    reason
            ));

            // wpis dla NADAWCY (kwota ujemna)
            log(new EconomyTxn(
                    ts,
                    Currency.DOLARY,
                    Action.PAY,
                    from,
                    from,
                    -amount,
                    beforeFrom,
                    afterFrom,
                    actor,
                    reason
            ));
        }

        return ok;
    }

    private void log(EconomyTxn txn) {
        if (history != null && txn != null) history.appendAsync(txn);
    }
}