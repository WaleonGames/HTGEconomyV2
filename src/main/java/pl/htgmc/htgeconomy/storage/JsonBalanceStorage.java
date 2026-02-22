package pl.htgmc.htgeconomy.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonBalanceStorage {

    private final Plugin plugin;
    private final File file;

    // thread-safe mapa w RAM
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();

    // prosty lock na I/O
    private final Object ioLock = new Object();

    public JsonBalanceStorage(Plugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    public void load() {
        synchronized (ioLock) {
            try {
                if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                if (!file.exists()) {
                    saveNow(); // utwórz pusty plik
                    return;
                }

                String json = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                balances.clear();

                if (json.isEmpty() || json.equals("{}")) return;

                // Minimalny parser JSON dla formatu: {"uuid":123,"uuid2":0}
                // (bez bibliotek, bo chciałeś prosto)
                json = json.strip();
                if (json.startsWith("{")) json = json.substring(1);
                if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

                if (json.trim().isEmpty()) return;

                String[] entries = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                for (String e : entries) {
                    String[] kv = e.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
                    if (kv.length != 2) continue;

                    String k = kv[0].trim();
                    String v = kv[1].trim();

                    k = unquote(k);
                    v = v.trim();
                    if (v.endsWith(",")) v = v.substring(0, v.length() - 1);

                    UUID uuid;
                    long bal;
                    try {
                        uuid = UUID.fromString(k);
                        bal = Long.parseLong(v.trim());
                    } catch (Exception ignore) {
                        continue;
                    }
                    balances.put(uuid, Math.max(0L, bal));
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("JSON load failed: " + file.getName() + " -> " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveNow);
    }

    public void saveNow() {
        synchronized (ioLock) {
            try {
                if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

                // Stabilny zapis (tmp + replace)
                File tmp = new File(file.getParentFile(), file.getName() + ".tmp");

                String json = toJson(balances);

                try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                    w.write(json);
                }

                Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                plugin.getLogger().severe("JSON save failed: " + file.getName() + " -> " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public long get(UUID uuid) {
        return balances.getOrDefault(uuid, 0L);
    }

    public void set(UUID uuid, long newBalance) {
        balances.put(uuid, Math.max(0L, newBalance));
    }

    public void add(UUID uuid, long amount) {
        if (amount <= 0) return;
        balances.merge(uuid, amount, Long::sum);
    }

    public boolean take(UUID uuid, long amount) {
        if (amount <= 0) return true;
        long cur = get(uuid);
        if (cur < amount) return false;
        set(uuid, cur - amount);
        return true;
    }

    public boolean transfer(UUID from, UUID to, long amount) {
        if (amount <= 0) return true;
        if (from.equals(to)) return true;

        // prosta "atomowość" na poziomie RAM (w 1 wątku komendy i tak jest sync)
        long fromBal = get(from);
        if (fromBal < amount) return false;

        set(from, fromBal - amount);
        add(to, amount);
        return true;
    }

    private static String toJson(Map<UUID, Long> map) {
        // Format: {"uuid":123,"uuid2":0}
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey().toString()).append("\":").append(e.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    public Map<UUID, Long> snapshot() {
        synchronized (this) {
            return new HashMap<>(balances); // <-- jeśli u Ciebie mapa nazywa się inaczej, podmień "balances"
        }
    }
}