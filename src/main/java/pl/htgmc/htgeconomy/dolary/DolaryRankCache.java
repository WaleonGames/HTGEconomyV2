package pl.htgmc.htgeconomy.dolary;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DolaryRankCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, String>>(){}.getType();

    private final JavaPlugin plugin;
    private final File file;

    // uuid -> rankKey ("vip", "mvip" ...)
    private final ConcurrentHashMap<UUID, String> map = new ConcurrentHashMap<>();

    public DolaryRankCache(JavaPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    public void load() {
        try {
            if (!file.exists()) return;
            try (FileReader r = new FileReader(file)) {
                Map<String, String> raw = GSON.fromJson(r, TYPE);
                if (raw == null) return;
                raw.forEach((k, v) -> {
                    try {
                        UUID uuid = UUID.fromString(k);
                        if (v != null && !v.isBlank()) map.put(uuid, v.toLowerCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {}
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Nie udało się wczytać ranks.json: " + e.getMessage());
        }
    }

    public void saveNow() {
        try {
            file.getParentFile().mkdirs();
            Map<String, String> out = new java.util.LinkedHashMap<>();
            for (var e : map.entrySet()) out.put(e.getKey().toString(), e.getValue());

            try (FileWriter w = new FileWriter(file)) {
                GSON.toJson(out, w);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Nie udało się zapisać ranks.json: " + e.getMessage());
        }
    }

    public void set(UUID uuid, String rankKey) {
        if (uuid == null) return;
        if (rankKey == null || rankKey.isBlank()) {
            map.remove(uuid);
            return;
        }
        map.put(uuid, rankKey.toLowerCase(Locale.ROOT));
    }

    public String get(UUID uuid) {
        if (uuid == null) return null;
        return map.get(uuid);
    }

    public Set<UUID> getKnownUuids() {
        return java.util.Collections.unmodifiableSet(map.keySet());
    }
}