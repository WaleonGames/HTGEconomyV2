package pl.htgmc.htgeconomy.api;

import org.bukkit.OfflinePlayer;
import pl.htgmc.htgeconomy.history.Currency;
import pl.htgmc.htgeconomy.history.EconomyTxn;

import java.util.List;
import java.util.UUID;

public interface HTGEconomyAPI {

    // ---- DOLARY
    long getDolary(UUID uuid);
    void setDolary(UUID uuid, long balance);
    void addDolary(UUID uuid, long amount);
    boolean takeDolary(UUID uuid, long amount);
    boolean payDolary(UUID from, UUID to, long amount);

    // ---- VPLN
    long getVpln(UUID uuid);
    void setVpln(UUID uuid, long balance);
    void addVpln(UUID uuid, long amount);
    boolean takeVpln(UUID uuid, long amount);
    boolean payVpln(UUID from, UUID to, long amount);

    // ---- MNOŻNIKI
    double getDolaryMultiplier(OfflinePlayer player);
    double getServerMultiplier();
    long toServerDynamicPrice(long basePrice);
    long toPlayerDynamicPrice(long basePrice, OfflinePlayer player);

    void recalcServerMultiplierNow();

    // ---- HISTORIA
    /**
     * Zwraca ostatnie wpisy historii dla gracza.
     * currencyOrNull = null -> wszystkie waluty (jeśli kiedyś chcesz),
     * currencyOrNull = DOLARY/VPLN -> filtr po walucie.
     */
    List<EconomyTxn> getRecentHistory(UUID target, Currency currencyOrNull, int limit);
}