package pl.htgmc.htgeconomy.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonBalanceStorage {

    private final Plugin plugin;
    private final File file;

    // thread-safe mapa w RAM
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

    // prosty lock na I/O
    private final Object ioLock = new Object();

    public JsonBalanceStorage(Plugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
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

                // Minimalny parser JSON dla formatu:
                // {"uuid":123,"uuid2":0.85}
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
                    double bal;
                    try {
                        uuid = UUID.fromString(k);
                        bal = Double.parseDouble(v.trim());
                    } catch (Exception ignore) {
                        continue;
                    }

                    balances.put(uuid, Math.max(0.0, round2(bal)));
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

    public double getDouble(UUID uuid) {
        if (uuid == null) return 0.0;
        return round2(balances.getOrDefault(uuid, 0.0));
    }

    public long get(UUID uuid) {
        return Math.round(getDouble(uuid));
    }

    public void setDouble(UUID uuid, double newBalance) {
        if (uuid == null) return;
        balances.put(uuid, Math.max(0.0, round2(newBalance)));
    }

    public void set(UUID uuid, long newBalance) {
        setDouble(uuid, newBalance);
    }

    public void addDouble(UUID uuid, double amount) {
        if (uuid == null) return;
        double delta = round2(amount);
        if (delta <= 0.0) return;

        balances.merge(uuid, delta, (a, b) -> round2(a + b));
    }

    public void add(UUID uuid, long amount) {
        addDouble(uuid, amount);
    }

    public boolean takeDouble(UUID uuid, double amount) {
        if (uuid == null) return false;

        double delta = round2(amount);
        if (delta <= 0.0) return true;

        double cur = getDouble(uuid);
        if (cur + 0.0000001 < delta) return false;

        setDouble(uuid, cur - delta);
        return true;
    }

    public boolean take(UUID uuid, long amount) {
        return takeDouble(uuid, amount);
    }

    public boolean transferDouble(UUID from, UUID to, double amount) {
        if (from == null || to == null) return false;
        if (amount <= 0.0) return true;
        if (from.equals(to)) return true;

        double delta = round2(amount);
        double fromBal = getDouble(from);
        if (fromBal + 0.0000001 < delta) return false;

        setDouble(from, fromBal - delta);
        addDouble(to, delta);
        return true;
    }

    public boolean transfer(UUID from, UUID to, long amount) {
        return transferDouble(from, to, amount);
    }

    private static String toJson(Map<UUID, Double> map) {
        // Format: {"uuid":123.00,"uuid2":0.85}
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;

        for (var e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;

            double value = round2(e.getValue());
            sb.append("\"")
                    .append(e.getKey().toString())
                    .append("\":")
                    .append(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString());
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

    public Map<UUID, Double> snapshotDouble() {
        synchronized (ioLock) {
            return new HashMap<>(balances);
        }
    }

    public Map<UUID, Long> snapshot() {
        Map<UUID, Long> out = new HashMap<>();
        synchronized (ioLock) {
            for (var e : balances.entrySet()) {
                out.put(e.getKey(), Math.round(e.getValue()));
            }
        }
        return out;
    }
}