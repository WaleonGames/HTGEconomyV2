package pl.htgmc.htgeconomy.history;

import java.util.UUID;

public record EconomyTxn(
        long timestamp,
        Currency currency,
        Action action,
        UUID target,
        UUID other,
        double amount,
        double before,
        double after,
        UUID actor,
        String reason
) {}