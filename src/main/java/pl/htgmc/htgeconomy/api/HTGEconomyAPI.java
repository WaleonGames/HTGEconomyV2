package pl.htgmc.htgeconomy.api;

import org.bukkit.OfflinePlayer;
import pl.htgmc.htgeconomy.history.Currency;
import pl.htgmc.htgeconomy.history.EconomyTxn;

import java.util.List;
import java.util.UUID;

public interface HTGEconomyAPI {

    // ---- DOLARY
    double getDolary(UUID uuid);
    void setDolary(UUID uuid, double balance);
    void addDolary(UUID uuid, double amount);
    boolean takeDolary(UUID uuid, double amount);
    boolean payDolary(UUID from, UUID to, double amount);

    // ---- VPLN
    double getVpln(UUID uuid);
    void setVpln(UUID uuid, double balance);
    void addVpln(UUID uuid, double amount);
    boolean takeVpln(UUID uuid, double amount);
    boolean payVpln(UUID from, UUID to, double amount);

    // ---- MNOŻNIKI
    double getDolaryMultiplier(OfflinePlayer player);
    double getServerMultiplier();
    long toServerDynamicPrice(long basePrice);
    long toPlayerDynamicPrice(long basePrice, OfflinePlayer player);
    void recalcServerMultiplierNow();

    // ---- HISTORIA
    List<EconomyTxn> getRecentHistory(UUID target, Currency currencyOrNull, int limit);
}