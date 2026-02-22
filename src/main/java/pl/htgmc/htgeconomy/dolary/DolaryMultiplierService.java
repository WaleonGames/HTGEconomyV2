package pl.htgmc.htgeconomy.dolary;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class DolaryMultiplierService {

    // Ustalony porządek ważności (astra > mvip > vip > default)
    public enum Rank {
        DEFAULT("default", 1.00),
        VIP("vip", 1.10),
        MVIP("mvip", 1.20),
        ASTRA("astra", 1.30);

        public final String key;
        public final double mult;

        Rank(String key, double mult) {
            this.key = key;
            this.mult = mult;
        }
    }

    private final Permission vaultPerms; // może być null
    private final DolaryRankCache cache; // nie null

    public DolaryMultiplierService(Permission vaultPerms, DolaryRankCache cache) {
        this.vaultPerms = vaultPerms;
        this.cache = cache;
    }

    /** Lista rang (do GUI / do debug). */
    public List<Rank> getRanks() {
        return Arrays.asList(Rank.values());
    }

    /** Mnożnik bazowy dla konkretnej rangi. */
    public double getRankMultiplier(Rank rank) {
        return rank == null ? Rank.DEFAULT.mult : rank.mult;
    }

    /**
     * Ranga gracza (online/offline).
     * - ONLINE: Vault primary group -> fallback permission-node -> default
     * - OFFLINE: Vault primary group (jeśli provider wspiera) -> fallback cache -> default
     */
    public Rank getRank(OfflinePlayer op) {
        if (op == null) return Rank.DEFAULT;

        // ONLINE: najpewniejsza ścieżka + aktualizacja cache
        if (op.isOnline()) {
            Player p = op.getPlayer();
            Rank r = getRankOnline(p);
            cache.set(op.getUniqueId(), r.key);
            return r;
        }

        // OFFLINE: spróbuj pobrać primary group przez reflection (różne wersje Vault/Permission)
        String group = getPrimaryGroupOffline(op);
        Rank byGroup = fromGroup(group);
        if (byGroup != null) {
            cache.set(op.getUniqueId(), byGroup.key);
            return byGroup;
        }

        // OFFLINE: fallback cache (ostatnio znana ranga)
        Rank cached = fromGroup(cache.get(op.getUniqueId()));
        return cached != null ? cached : Rank.DEFAULT;
    }

    /** Finalny mnożnik gracza. */
    public double getMultiplier(OfflinePlayer op) {
        return getRank(op).mult;
    }

    /** Przelicza cenę bazową na dynamiczną (zaokrąglanie do long). */
    public long toDynamicPrice(long basePrice, OfflinePlayer op) {
        if (basePrice <= 0) return 0L;
        double m = getMultiplier(op);
        long out = Math.round(basePrice * m);
        return Math.max(0L, out);
    }

    /** Odśwież cache rang dla wszystkich ONLINE (np. na starcie / po reload perms). */
    public void refreshAllOnlineToCache() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Rank r = getRankOnline(p);
            cache.set(p.getUniqueId(), r.key);
        }
    }

    // --- helpers

    private Rank getRankOnline(Player p) {
        if (p == null) return Rank.DEFAULT;

        // 1) Vault perms -> primary group
        String group = null;
        try {
            if (vaultPerms != null) group = vaultPerms.getPrimaryGroup(p); // ta metoda jest pewna w API
        } catch (Throwable ignored) {}

        Rank byGroup = fromGroup(group);
        if (byGroup != null) return byGroup;

        // 2) Fallback: permission nodes (najwyższa wygrywa) - tylko ONLINE
        if (p.hasPermission("htgeco.rank.astra")) return Rank.ASTRA;
        if (p.hasPermission("htgeco.rank.mvip")) return Rank.MVIP;
        if (p.hasPermission("htgeco.rank.vip")) return Rank.VIP;

        return Rank.DEFAULT;
    }

    /**
     * OFFLINE primary group – bez zależności od konkretnej wersji Vault.
     * Próbujemy kilka wariantów metod (jeśli istnieją), przez reflection:
     * 1) getPrimaryGroup(String world, OfflinePlayer player)
     * 2) getPrimaryGroup(String world, String playerName)
     *
     * Jeśli nic nie działa -> null (wtedy poleci cache).
     */
    private String getPrimaryGroupOffline(OfflinePlayer op) {
        if (vaultPerms == null || op == null) return null;

        // worldName: bierzemy pierwszy świat (zwykle "world"). Bezpieczne dla większości perms providerów.
        String worldName = null;
        try {
            if (!Bukkit.getWorlds().isEmpty()) worldName = Bukkit.getWorlds().getFirst().getName();
        } catch (Throwable ignored) {}

        // 1) getPrimaryGroup(String, OfflinePlayer)
        try {
            Method m = vaultPerms.getClass().getMethod("getPrimaryGroup", String.class, OfflinePlayer.class);
            Object out = m.invoke(vaultPerms, worldName, op);
            if (out instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {}

        // 2) getPrimaryGroup(String, String)
        try {
            String name = op.getName();
            if (name != null && !name.isBlank()) {
                Method m = vaultPerms.getClass().getMethod("getPrimaryGroup", String.class, String.class);
                Object out = m.invoke(vaultPerms, worldName, name);
                if (out instanceof String s && !s.isBlank()) return s;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static Rank fromGroup(String group) {
        if (group == null || group.isBlank()) return null;
        String g = group.toLowerCase(Locale.ROOT);

        if (g.equals("astra")) return Rank.ASTRA;
        if (g.equals("mvip")) return Rank.MVIP;
        if (g.equals("vip")) return Rank.VIP;
        if (g.equals("default")) return Rank.DEFAULT;

        return null;
    }
}