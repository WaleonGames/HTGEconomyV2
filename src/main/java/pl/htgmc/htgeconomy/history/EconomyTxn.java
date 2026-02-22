package pl.htgmc.htgeconomy.history;

import java.util.UUID;

public record EconomyTxn(
        long tsMillis,
        Currency currency,
        Action action,

        UUID target,         // kto dostał zmianę (dla PAY: odbiorca)
        UUID source,         // dla PAY: nadawca, inaczej null

        long amount,         // dla SET: nowa wartość (targetBalance), a amount = różnica? -> patrz niżej (u nas: amount = "kwota operacji")
        long balanceBefore,  // saldo target przed operacją (jeśli znane)
        long balanceAfter,   // saldo target po operacji (jeśli znane)

        UUID actor,          // kto wykonał (komenda/admin/system) - może być null
        String reason        // opcjonalnie: "quest", "shop", "admin" itd.
) {}