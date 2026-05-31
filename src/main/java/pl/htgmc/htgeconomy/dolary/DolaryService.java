package pl.htgmc.htgeconomy.dolary;

import pl.htgmc.htgeconomy.history.Action;
import pl.htgmc.htgeconomy.history.Currency;
import pl.htgmc.htgeconomy.history.EconomyHistoryStore;
import pl.htgmc.htgeconomy.history.EconomyTxn;
import pl.htgmc.htgeconomy.storage.JsonBalanceStorage;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public double get(UUID uuid) {
        if (uuid == null) return 0.0;
        return round2(storage.getDouble(uuid));
    }

    // --- stare metody (kompatybilność)

    public void set(UUID uuid, double bal) {
        set(uuid, bal, null, null);
    }

    public void add(UUID uuid, double amount) {
        add(uuid, amount, null, null);
    }

    public boolean take(UUID uuid, double amount) {
        return take(uuid, amount, null, null);
    }

    public boolean pay(UUID from, UUID to, double amount) {
        return pay(from, to, amount, null, null);
    }

    // --- nowe metody (z historią)

    public void set(UUID uuid, double bal, UUID actor, String reason) {
        if (uuid == null) return;

        double target = Math.max(0.0, round2(bal));
        double before = Math.max(0.0, round2(storage.getDouble(uuid)));
        double after = target;

        storage.setDouble(uuid, target);
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

    public void add(UUID uuid, double amount, UUID actor, String reason) {
        if (uuid == null) return;

        double delta = round2(amount);
        if (delta <= 0.0) return;

        double before = Math.max(0.0, round2(storage.getDouble(uuid)));
        double after = round2(before + delta);

        storage.setDouble(uuid, after);
        storage.saveAsync();

        log(new EconomyTxn(
                System.currentTimeMillis(),
                Currency.DOLARY,
                Action.ADD,
                uuid,
                null,
                delta,
                before,
                after,
                actor,
                reason
        ));
    }

    public boolean take(UUID uuid, double amount, UUID actor, String reason) {
        if (uuid == null) return false;

        double delta = round2(amount);
        if (delta <= 0.0) return true;

        double before = Math.max(0.0, round2(storage.getDouble(uuid)));
        if (before + 0.0000001 < delta) return false;

        double after = round2(before - delta);

        storage.setDouble(uuid, after);
        storage.saveAsync();

        log(new EconomyTxn(
                System.currentTimeMillis(),
                Currency.DOLARY,
                Action.TAKE,
                uuid,
                null,
                delta,
                before,
                after,
                actor,
                reason
        ));

        return true;
    }

    public boolean pay(UUID from, UUID to, double amount, UUID actor, String reason) {
        if (from == null || to == null) return false;

        double delta = round2(amount);
        if (delta <= 0.0) return true;

        double beforeFrom = Math.max(0.0, round2(storage.getDouble(from)));
        if (beforeFrom + 0.0000001 < delta) return false;

        double beforeTo = Math.max(0.0, round2(storage.getDouble(to)));

        double afterFrom = round2(beforeFrom - delta);
        double afterTo = round2(beforeTo + delta);

        storage.setDouble(from, afterFrom);
        storage.setDouble(to, afterTo);
        storage.saveAsync();

        long ts = System.currentTimeMillis();

        log(new EconomyTxn(
                ts,
                Currency.DOLARY,
                Action.PAY,
                to,
                from,
                delta,
                beforeTo,
                afterTo,
                actor,
                reason
        ));

        log(new EconomyTxn(
                ts,
                Currency.DOLARY,
                Action.PAY,
                from,
                from,
                -delta,
                beforeFrom,
                afterFrom,
                actor,
                reason
        ));

        return true;
    }

    private void log(EconomyTxn txn) {
        if (history != null && txn != null) {
            history.appendAsync(txn);
        }
    }
}