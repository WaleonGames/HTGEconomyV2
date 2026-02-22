package pl.htgmc.htgeconomy.api;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import pl.htgmc.htgeconomy.dolary.DolaryMultiplierService;
import pl.htgmc.htgeconomy.dolary.DolaryServerMultiplierService;
import pl.htgmc.htgeconomy.dolary.DolaryService;
import pl.htgmc.htgeconomy.history.Currency;
import pl.htgmc.htgeconomy.history.EconomyHistoryStore;
import pl.htgmc.htgeconomy.history.EconomyTxn;
import pl.htgmc.htgeconomy.vpln.VplnService;

import java.util.List;
import java.util.UUID;

public final class HTGEconomyAPIImpl implements HTGEconomyAPI {

    private final DolaryService dolary;
    private final VplnService vpln;

    private final Economy vaultEco; // może być null
    private final DolaryMultiplierService mult;
    private final DolaryServerMultiplierService serverMult;

    private final EconomyHistoryStore history; // może być null

    /**
     * STARY konstruktor (kompatybilność) - bez historii.
     */
    public HTGEconomyAPIImpl(DolaryService dolary,
                             VplnService vpln,
                             Economy vaultEco,
                             DolaryMultiplierService mult,
                             DolaryServerMultiplierService serverMult) {
        this(dolary, vpln, vaultEco, mult, serverMult, null);
    }

    /**
     * NOWY konstruktor - z historią.
     */
    public HTGEconomyAPIImpl(DolaryService dolary,
                             VplnService vpln,
                             Economy vaultEco,
                             DolaryMultiplierService mult,
                             DolaryServerMultiplierService serverMult,
                             EconomyHistoryStore history) {
        this.dolary = dolary;
        this.vpln = vpln;
        this.vaultEco = vaultEco;
        this.mult = mult;
        this.serverMult = serverMult;
        this.history = history;
    }

    // ---- DOLARY

    @Override
    public long getDolary(UUID uuid) {
        if (vaultEco == null) return dolary.get(uuid);
        return (long) vaultEco.getBalance(Bukkit.getOfflinePlayer(uuid));
    }

    @Override
    public void setDolary(UUID uuid, long balance) {
        long bal = Math.max(0L, balance);

        if (vaultEco == null) {
            dolary.set(uuid, bal);
            return;
        }

        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        double cur = vaultEco.getBalance(p);
        double target = bal;
        double diff = target - cur;

        if (diff > 0) vaultEco.depositPlayer(p, diff);
        else if (diff < 0) vaultEco.withdrawPlayer(p, -diff);
    }

    @Override
    public void addDolary(UUID uuid, long amount) {
        if (amount <= 0) return;
        if (vaultEco == null) { dolary.add(uuid, amount); return; }
        vaultEco.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
    }

    @Override
    public boolean takeDolary(UUID uuid, long amount) {
        if (amount <= 0) return true;
        if (vaultEco == null) return dolary.take(uuid, amount);

        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        var resp = vaultEco.withdrawPlayer(p, amount);
        return resp != null && resp.type == net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS;
    }

    @Override
    public boolean payDolary(UUID from, UUID to, long amount) {
        if (amount <= 0) return true;
        if (vaultEco == null) return dolary.pay(from, to, amount);

        OfflinePlayer pf = Bukkit.getOfflinePlayer(from);
        OfflinePlayer pt = Bukkit.getOfflinePlayer(to);

        var w = vaultEco.withdrawPlayer(pf, amount);
        if (w == null || w.type != net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS) return false;

        var d = vaultEco.depositPlayer(pt, amount);
        if (d == null || d.type != net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS) {
            vaultEco.depositPlayer(pf, amount); // rollback
            return false;
        }
        return true;
    }

    // ---- VPLN

    @Override public long getVpln(UUID uuid) { return vpln.get(uuid); }
    @Override public void setVpln(UUID uuid, long balance) { vpln.set(uuid, Math.max(0L, balance)); }
    @Override public void addVpln(UUID uuid, long amount) { if (amount > 0) vpln.add(uuid, amount); }
    @Override public boolean takeVpln(UUID uuid, long amount) { return amount <= 0 || vpln.take(uuid, amount); }
    @Override public boolean payVpln(UUID from, UUID to, long amount) { return amount <= 0 || vpln.pay(from, to, amount); }

    // ---- MNOŻNIKI

    @Override
    public double getDolaryMultiplier(OfflinePlayer player) {
        return mult.getMultiplier(player);
    }

    @Override
    public double getServerMultiplier() {
        return serverMult.getServerMultiplier();
    }

    @Override
    public long toServerDynamicPrice(long basePrice) {
        return serverMult.toServerDynamicPrice(basePrice);
    }

    @Override
    public long toPlayerDynamicPrice(long basePrice, OfflinePlayer player) {
        return mult.toDynamicPrice(basePrice, player);
    }

    @Override
    public void recalcServerMultiplierNow() {
        serverMult.recalcNow();
    }

    // ---- HISTORIA

    @Override
    public List<EconomyTxn> getRecentHistory(UUID target, Currency currencyOrNull, int limit) {
        if (history == null) return List.of();
        return history.getRecent(target, currencyOrNull, limit);
    }
}