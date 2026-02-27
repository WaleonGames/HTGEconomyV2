package pl.htgmc.htgeconomy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pl.htgmc.htgeconomy.api.HTGEconomyAPI;
import pl.htgmc.htgeconomy.api.HTGEconomyAPIImpl;
import pl.htgmc.htgeconomy.commands.EcoServerCommand;
import pl.htgmc.htgeconomy.commands.MoneyCommand;
import pl.htgmc.htgeconomy.dolary.*;
import pl.htgmc.htgeconomy.gui.EcoServerGUI;
import pl.htgmc.htgeconomy.gui.EcoServerListener;
import pl.htgmc.htgeconomy.history.Currency;
import pl.htgmc.htgeconomy.history.EconomyHistoryStore;
import pl.htgmc.htgeconomy.history.EconomyTxn;
import pl.htgmc.htgeconomy.placeholder.EconomyExpansion;
import pl.htgmc.htgeconomy.storage.JsonBalanceStorage;
import pl.htgmc.htgeconomy.vpln.VplnService;

import java.io.File;
import java.util.List;
import java.util.UUID;

public final class HTGEconomyPlugin extends JavaPlugin {

    private JsonBalanceStorage dolaryStore;
    private JsonBalanceStorage vplnStore;

    private DolaryService dolary;
    private VplnService vpln;

    // historia (txns.jsonl)
    private EconomyHistoryStore history;

    // Vault economy (dla /dolary i dla innych pluginów)
    private Economy vaultEco;

    // Vault perms (LuckPerms przez Vault) - opcjonalne
    private Permission vaultPerms;

    // Cache rang (offline support)
    private DolaryRankCache rankCache;

    // Mnożniki dolarów (default/vip/mvip/astra) - per gracz
    private DolaryMultiplierService dolaryMult;

    // Globalny mnożnik serwera (online + opcjonalnie offline z cache)
    private DolaryServerMultiplierService serverMult;

    // API
    private HTGEconomyAPI api;

    @Override
    public void onEnable() {
        // Historia (append-only JSONL)
        history = new EconomyHistoryStore(this, new File(getDataFolder(), "history/txns.jsonl"));

        // /dolary/balances.json
        dolaryStore = new JsonBalanceStorage(this, new File(getDataFolder(), "dolary/balances.json"));
        dolaryStore.load();
        dolary = new DolaryService(dolaryStore, history);

        // /vpln/balances.json
        vplnStore = new JsonBalanceStorage(this, new File(getDataFolder(), "vpln/balances.json"));
        vplnStore.load();
        vpln = new VplnService(vplnStore, history);

        // 1) Rejestracja Vault provider dla DOLARY
        new VaultHook(this).register(dolary);

        // 2) Pobranie Economy z ServicesManager (nasz provider, albo inny jeśli ktoś nadpisał)
        vaultEco = resolveVaultEconomy();

        // 2.1) Pobranie Vault Permission provider (LuckPerms przez Vault) - opcjonalne
        vaultPerms = resolveVaultPerms();

        // 2.2) Rank cache (TRWAŁY, nie temp)
        rankCache = new DolaryRankCache(this, new File(getDataFolder(), "dolary/ranks.json"));
        rankCache.load();

        // 2.3) Mnożniki dolarów (per gracz)
        dolaryMult = new DolaryMultiplierService(vaultPerms, rankCache);
        dolaryMult.refreshAllOnlineToCache();
        rankCache.saveNow();

        // 2.4) Globalny mnożnik serwera (średnia z online + offline z cache)
        serverMult = new DolaryServerMultiplierService(
                this,
                dolaryMult,
                rankCache,
                DolaryServerMultiplierService.SampleMode.ONLINE_PLUS_CACHED_OFFLINE
        );
        serverMult.start();

        // 2.45) API (ServicesManager)
        api = new HTGEconomyAPIImpl(dolary, vpln, vaultEco, dolaryMult, serverMult, history);
        getServer().getServicesManager().register(HTGEconomyAPI.class, api, this, ServicePriority.Normal);

        // 2.5) PlaceholderAPI (placeholdery ekonomii) - soft
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new EconomyExpansion(this, dolary, vpln, dolaryMult, serverMult).register();
                getLogger().info("PlaceholderAPI: zarejestrowano placeholdery %htgeco_*%.");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI: nie udało się zarejestrować placeholderów: " + t.getMessage());
            }
        } else {
            getLogger().info("PlaceholderAPI: brak — pomijam rejestrację placeholderów.");
        }

        // 3) Komendy

        // /dolary -> przez Vault (spójne z całym serwerem)
        registerMoneyCommand("dolary", "$", new MoneyCommand.Backend() {
            @Override public long get(UUID uuid) {
                if (vaultEco == null) return dolary.get(uuid);
                return (long) vaultEco.getBalance(getServer().getOfflinePlayer(uuid));
            }

            @Override public void set(UUID uuid, long bal) {
                if (vaultEco == null) { dolary.set(uuid, bal); return; }
                var p = getServer().getOfflinePlayer(uuid);
                double cur = vaultEco.getBalance(p);
                double target = Math.max(0, bal);
                double diff = target - cur;
                if (diff > 0) vaultEco.depositPlayer(p, diff);
                else if (diff < 0) vaultEco.withdrawPlayer(p, -diff);

                // historia (Vault path też logujemy)
                dolary.set(uuid, (long) target, null, "vault:set");
            }

            @Override public void add(UUID uuid, long amount) {
                if (amount <= 0) return;

                if (vaultEco == null) { dolary.add(uuid, amount); return; }
                vaultEco.depositPlayer(getServer().getOfflinePlayer(uuid), amount);

                // historia
                dolary.add(uuid, amount, null, "vault:add");
            }

            @Override public boolean take(UUID uuid, long amount) {
                if (amount <= 0) return true;

                if (vaultEco == null) return dolary.take(uuid, amount);

                var p = getServer().getOfflinePlayer(uuid);
                var resp = vaultEco.withdrawPlayer(p, amount);
                boolean ok = resp != null && resp.type == net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS;

                if (ok) dolary.take(uuid, amount, null, "vault:take");
                return ok;
            }

            @Override public boolean pay(UUID from, UUID to, long amount) {
                if (amount <= 0) return true;

                if (vaultEco == null) return dolary.pay(from, to, amount);

                var pf = getServer().getOfflinePlayer(from);
                var pt = getServer().getOfflinePlayer(to);

                var w = vaultEco.withdrawPlayer(pf, amount);
                if (w == null || w.type != net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS) return false;

                var d = vaultEco.depositPlayer(pt, amount);
                if (d == null || d.type != net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS) {
                    // rollback jeśli deposit nie wyszedł
                    vaultEco.depositPlayer(pf, amount);
                    return false;
                }

                // historia
                dolary.pay(from, to, amount, null, "vault:pay");
                return true;
            }
        });

        // /vpln -> zostaje JSON service
        registerMoneyCommand("vpln", "VPLN", new MoneyCommand.Backend() {
            @Override public long get(UUID uuid) { return vpln.get(uuid); }
            @Override public void set(UUID uuid, long bal) { vpln.set(uuid, bal, null, "cmd:set"); }
            @Override public void add(UUID uuid, long amount) { vpln.add(uuid, amount, null, "cmd:add"); }
            @Override public boolean take(UUID uuid, long amount) { return vpln.take(uuid, amount, null, "cmd:take"); }
            @Override public boolean pay(UUID from, UUID to, long amount) { return vpln.pay(from, to, amount, null, "cmd:pay"); }
        });

        // 4) GUI /eco-server
        EcoServerGUI ecoGui = new EcoServerGUI(dolaryStore, vplnStore, dolaryMult, serverMult);
        PluginCommand ecoCmd = getCommand("eco-server");
        if (ecoCmd != null) {
            ecoCmd.setExecutor(new EcoServerCommand(ecoGui));
        } else {
            getLogger().severe("Brak komendy w plugin.yml: eco-server");
        }
        getServer().getPluginManager().registerEvents(new EcoServerListener(ecoGui, serverMult), this);

        getLogger().info("HTGEconomy enabled: dolary(Vault+JSON) + vpln(JSON) + multiplier(per-player + server-global) + API + HISTORY");
    }

    @Override
    public void onDisable() {
        try { if (dolaryStore != null) dolaryStore.saveNow(); } catch (Exception ignored) {}
        try { if (vplnStore != null) vplnStore.saveNow(); } catch (Exception ignored) {}
        try { if (rankCache != null) rankCache.saveNow(); } catch (Exception ignored) {}

        try {
            if (api != null) getServer().getServicesManager().unregister(HTGEconomyAPI.class, api);
        } catch (Throwable ignored) {}

        try { if (history != null) history.flushNow(); } catch (Throwable ignored) {}
    }

    /** Getter pod plugin-instance (opcjonalnie, jak ktoś nie chce ServicesManager). */
    public HTGEconomyAPI api() {
        return api;
    }

    /** Alias pod inne pluginy (np. HTGModule) */
    public HTGEconomyAPI getApi() {
        return api;
    }

    private void registerMoneyCommand(String key, String symbol, MoneyCommand.Backend backend) {
        PluginCommand c = getCommand(key);
        if (c == null) {
            getLogger().severe("Brak komendy w plugin.yml: " + key);
            return;
        }
        MoneyCommand exec = new MoneyCommand(key, symbol, backend);
        c.setExecutor(exec);
        c.setTabCompleter(exec);
    }

    private Economy resolveVaultEconomy() {
        try {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                getLogger().warning("Vault Economy nie jest zarejestrowane (brak Vault lub provider). /dolary użyje fallback JSON.");
                return null;
            }
            getLogger().info("Vault Economy provider: " + rsp.getProvider().getName());
            return rsp.getProvider();
        } catch (Throwable t) {
            getLogger().warning("Nie udało się pobrać Vault Economy: " + t.getMessage());
            return null;
        }
    }

    private Permission resolveVaultPerms() {
        try {
            RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
            if (rsp == null) {
                getLogger().info("Vault Permission provider: brak (LuckPerms przez Vault nieaktywny). Offline będzie z cache ranks.json.");
                return null;
            }
            getLogger().info("Vault Permission provider: " + rsp.getProvider().getName());
            return rsp.getProvider();
        } catch (Throwable t) {
            getLogger().warning("Nie udało się pobrać Vault Permission: " + t.getMessage());
            return null;
        }
    }
}