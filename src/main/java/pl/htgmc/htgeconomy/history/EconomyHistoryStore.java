package pl.htgmc.htgeconomy.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EconomyHistoryStore {

    private static final Gson GSON = new GsonBuilder().create();

    private final JavaPlugin plugin;
    private final File file;

    private final ConcurrentLinkedQueue<EconomyTxn> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    public EconomyHistoryStore(JavaPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    /** Dopisz wpis do kolejki i zapisz async (append). */
    public void appendAsync(EconomyTxn txn) {
        if (txn == null) return;
        queue.add(txn);
        flushAsync();
    }

    /** Wymuś zapis wszystkiego teraz (na disable). */
    public void flushNow() {
        drainToFile();
    }

    /** Pobierz ostatnie N wpisów dla target (filtr opcjonalny: waluta). */
    public List<EconomyTxn> getRecent(UUID target, Currency currencyOrNull, int limit) {
        if (target == null) return List.of();
        if (limit <= 0) return List.of();

        if (!file.exists()) return List.of();

        // Na start: czytamy cały plik (OK przy małych plikach). Potem można zoptymalizować.
        List<EconomyTxn> all = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    EconomyTxn t = GSON.fromJson(line, EconomyTxn.class);
                    if (t == null) continue;
                    all.add(t);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("History read error: " + e.getMessage());
            return List.of();
        }

        // filtr + od końca
        List<EconomyTxn> out = new ArrayList<>(Math.min(limit, 64));
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            EconomyTxn t = all.get(i);
            if (!target.equals(t.target())) continue;
            if (currencyOrNull != null && currencyOrNull != t.currency()) continue;
            out.add(t);
        }
        return out;
    }

    // ----------------

    private void flushAsync() {
        if (!flushing.compareAndSet(false, true)) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                drainToFile();
            } finally {
                flushing.set(false);
                // jeśli coś doszło w trakcie, dobij jeszcze raz
                if (!queue.isEmpty()) flushAsync();
            }
        });
    }

    private void drainToFile() {
        List<EconomyTxn> batch = new ArrayList<>();
        for (;;) {
            EconomyTxn t = queue.poll();
            if (t == null) break;
            batch.add(t);
        }
        if (batch.isEmpty()) return;

        try {
            file.getParentFile().mkdirs();
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                for (EconomyTxn t : batch) {
                    bw.write(GSON.toJson(t));
                    bw.newLine();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("History write error: " + e.getMessage());
        }
    }
}