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
import pl.htgmc.htgeconomy.history.EconomyHistoryStore;
import pl.htgmc.htgeconomy.placeholder.EconomyExpansion;
import pl.htgmc.htgeconomy.storage.JsonBalanceStorage;
import pl.htgmc.htgeconomy.vpln.VplnService;

import java.io.File;
import java.util.UUID;

public final class HTGEconomyPlugin extends JavaPlugin {

    private JsonBalanceStorage dolaryStore;
    private JsonBalanceStorage vplnStore;

    private DolaryService dolary;
    private VplnService vpln;

    private EconomyHistoryStore history;
    private Economy vaultEco;
    private Permission vaultPerms;
    private DolaryRankCache rankCache;
    private DolaryMultiplierService dolaryMult;
    private DolaryServerMultiplierService serverMult;
    private HTGEconomyAPI api;

    @Override
    public void onEnable() {
        history = new EconomyHistoryStore(this, new File(getDataFolder(), "history/txns.jsonl"));

        dolaryStore = new JsonBalanceStorage(this, new File(getDataFolder(), "dolary/balances.json"));
        dolaryStore.load();
        dolary = new DolaryService(dolaryStore, history);

        vplnStore = new JsonBalanceStorage(this, new File(getDataFolder(), "vpln/balances.json"));
        vplnStore.load();
        vpln = new VplnService(vplnStore, history);

        new VaultHook(this).register(dolary);

        vaultEco = resolveVaultEconomy();
        vaultPerms = resolveVaultPerms();

        rankCache = new DolaryRankCache(this, new File(getDataFolder(), "dolary/ranks.json"));
        rankCache.load();

        dolaryMult = new DolaryMultiplierService(vaultPerms, rankCache);
        dolaryMult.refreshAllOnlineToCache();
        rankCache.saveNow();

        serverMult = new DolaryServerMultiplierService(
                this,
                dolaryMult,
                rankCache,
                DolaryServerMultiplierService.SampleMode.ONLINE_PLUS_CACHED_OFFLINE
        );
        serverMult.start();

        api = new HTGEconomyAPIImpl(dolary, vpln, vaultEco, dolaryMult, serverMult, history);
        getServer().getServicesManager().register(HTGEconomyAPI.class, api, this, ServicePriority.Normal);

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

        // /dolary -> przez Vault (obsługa 0.xx)
        registerMoneyCommand("dolary", "$", new MoneyCommand.Backend() {
            @Override
            public double get(UUID uuid) {
                if (vaultEco == null) return dolary.get(uuid);
                return vaultEco.getBalance(getServer().getOfflinePlayer(uuid));
            }

            @Override
            public void set(UUID uuid, double bal) {
                double target = Math.max(0.0, bal);

                if (vaultEco == null) {
                    dolary.set(uuid, target, null, "cmd:set");
                    return;
                }

                var p = getServer().getOfflinePlayer(uuid);
                double cur = vaultEco.getBalance(p);
                double diff = target - cur;

                if (diff > 0.0) {
                    vaultEco.depositPlayer(p, diff);
                } else if (diff < 0.0) {
                    vaultEco.withdrawPlayer(p, -diff);
                }
            }

            @Override
            public void add(UUID uuid, double amount) {
                if (amount <= 0.0) return;

                if (vaultEco == null) {
                    dolary.add(uuid, amount, null, "cmd:add");
                    return;
                }

                vaultEco.depositPlayer(getServer().getOfflinePlayer(uuid), amount);
            }

            @Override
            public boolean take(UUID uuid, double amount) {
                if (amount <= 0.0) return true;

                if (vaultEco == null) {
                    return dolary.take(uuid, amount, null, "cmd:take");
                }

                var p = getServer().getOfflinePlayer(uuid);
                var resp = vaultEco.withdrawPlayer(p, amount);
                return resp != null
                        && resp.type == net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS;
            }

            @Override
            public boolean pay(UUID from, UUID to, double amount) {
                if (amount <= 0.0) return true;

                if (vaultEco == null) {
                    return dolary.pay(from, to, amount, null, "cmd:pay");
                }

                var pf = getServer().getOfflinePlayer(from);
                var pt = getServer().getOfflinePlayer(to);

                var w = vaultEco.withdrawPlayer(pf, amount);
                if (w == null || w.type != net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS) {
                    return false;
                }

                var d = vaultEco.depositPlayer(pt, amount);
                if (d == null || d.type != net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS) {
                    vaultEco.depositPlayer(pf, amount);
                    return false;
                }

                return true;
            }
        });

        // /vpln -> pełna obsługa części dziesiętnych
        registerMoneyCommand("vpln", "VPLN", new MoneyCommand.Backend() {
            @Override
            public double get(UUID uuid) {
                return vpln.get(uuid);
            }

            @Override
            public void set(UUID uuid, double bal) {
                vpln.set(uuid, Math.max(0.0, bal), null, "cmd:set");
            }

            @Override
            public void add(UUID uuid, double amount) {
                if (amount <= 0.0) return;
                vpln.add(uuid, amount, null, "cmd:add");
            }

            @Override
            public boolean take(UUID uuid, double amount) {
                if (amount <= 0.0) return true;
                return vpln.take(uuid, amount, null, "cmd:take");
            }

            @Override
            public boolean pay(UUID from, UUID to, double amount) {
                if (amount <= 0.0) return true;
                return vpln.pay(from, to, amount, null, "cmd:pay");
            }
        });

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

    public HTGEconomyAPI api() {
        return api;
    }

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