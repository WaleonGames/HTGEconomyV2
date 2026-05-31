package pl.htgmc.htgeconomy.dolary;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public final class VaultEconomyProvider implements Economy {

    private final DolaryService dolary;

    public VaultEconomyProvider(DolaryService dolary) {
        this.dolary = dolary;
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static EconomyResponse ok(double amount, double balance) {
        return new EconomyResponse(round2(amount), round2(balance), EconomyResponse.ResponseType.SUCCESS, null);
    }

    private static EconomyResponse fail(String msg) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, msg);
    }

    private static final ThreadLocal<DecimalFormat> MONEY_FMT = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');

        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        df.setGroupingUsed(true);
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(2);
        return df;
    });

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "HTGEconomy-Dolary"; }

    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }

    @Override public String currencyNameSingular() { return "dolar"; }
    @Override public String currencyNamePlural() { return "dolary"; }

    @Override
    public String format(double amount) {
        return MONEY_FMT.get().format(round2(amount));
    }

    // --- Accounts (OfflinePlayer) ---
    @Override public boolean hasAccount(OfflinePlayer player) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return true; }

    @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return true; }

    // --- Balance / Has (OfflinePlayer) ---
    @Override
    public double getBalance(OfflinePlayer player) {
        return dolary.get(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        double a = round2(amount);
        if (a <= 0.0) return true;
        return dolary.get(player.getUniqueId()) + 0.0000001 >= a;
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    // --- Withdraw / Deposit (OfflinePlayer) ---
    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        double a = round2(amount);
        if (a < 0.0) return fail("amount<0");
        if (a == 0.0) return ok(0, getBalance(player));

        boolean success = dolary.take(player.getUniqueId(), a, null, "vault:withdraw");
        return success ? ok(a, getBalance(player)) : fail("insufficient funds");
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        double a = round2(amount);
        if (a < 0.0) return fail("amount<0");
        if (a == 0.0) return ok(0, getBalance(player));

        dolary.add(player.getUniqueId(), a, null, "vault:deposit");
        return ok(a, getBalance(player));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    // --- Name-based ---
    @Override public boolean hasAccount(String playerName) { return true; }
    @Override public boolean hasAccount(String playerName, String worldName) { return true; }

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        return getBalance(p);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(String playerName, double amount) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        return has(p, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        return withdrawPlayer(p, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        return depositPlayer(p, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override public boolean createPlayerAccount(String playerName) { return true; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return true; }

    // --- Bank (brak) ---
    @Override public EconomyResponse createBank(String name, String player) { return fail("no bank"); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return fail("no bank"); }
    @Override public EconomyResponse deleteBank(String name) { return fail("no bank"); }
    @Override public EconomyResponse bankBalance(String name) { return fail("no bank"); }
    @Override public EconomyResponse bankHas(String name, double amount) { return fail("no bank"); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return fail("no bank"); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return fail("no bank"); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return fail("no bank"); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return fail("no bank"); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return fail("no bank"); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return fail("no bank"); }
    @Override public List<String> getBanks() { return List.of(); }
}