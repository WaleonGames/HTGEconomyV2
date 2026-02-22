package pl.htgmc.htgeconomy.dolary;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class DolaryServerMultiplierService {

    public enum SampleMode {
        ONLINE_ONLY,
        ONLINE_PLUS_CACHED_OFFLINE
    }

    private final JavaPlugin plugin;
    private final DolaryMultiplierService perPlayer;
    private final DolaryRankCache rankCache; // może być null
    private final SampleMode mode;

    // odświeżanie na sztywno (bez configu): 30s
    private final long refreshTicks = 20L * 30L;

    private final AtomicReference<Snapshot> snap =
            new AtomicReference<>(Snapshot.empty());

    public DolaryServerMultiplierService(JavaPlugin plugin,
                                         DolaryMultiplierService perPlayer,
                                         DolaryRankCache rankCache,
                                         SampleMode mode) {
        this.plugin = plugin;
        this.perPlayer = perPlayer;
        this.rankCache = rankCache;
        this.mode = (mode == null ? SampleMode.ONLINE_ONLY : mode);
    }

    public void start() {
        // liczymy na MAIN thread (bezpieczniej z perms/group)
        Bukkit.getScheduler().runTaskTimer(plugin, this::recalcNow, 20L, refreshTicks);
        Bukkit.getScheduler().runTask(plugin, this::recalcNow);
    }

    /** Przelicz od razu (np. na klik "Odśwież" w GUI). */
    public void recalcNow() {
        int count = 0;
        double sum = 0.0;

        EnumMap<DolaryMultiplierService.Rank, Integer> rankCounts =
                new EnumMap<>(DolaryMultiplierService.Rank.class);
        for (DolaryMultiplierService.Rank r : DolaryMultiplierService.Rank.values()) {
            rankCounts.put(r, 0);
        }

        // 1) ONLINE
        for (Player p : Bukkit.getOnlinePlayers()) {
            DolaryMultiplierService.Rank r = perPlayer.getRank(p); // Player -> OfflinePlayer (OK)
            double m = r.mult;

            rankCounts.put(r, rankCounts.get(r) + 1);
            sum += m;
            count++;
        }

        // 2) OFFLINE z cache (opcjonalnie)
        if (mode == SampleMode.ONLINE_PLUS_CACHED_OFFLINE && rankCache != null) {
            Set<UUID> known = rankCache.getKnownUuids();

            for (UUID uuid : known) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                if (op.isOnline()) continue; // już policzony jako online

                DolaryMultiplierService.Rank r = perPlayer.getRank(op); // Vault offline lub cache
                double m = r.mult;

                rankCounts.put(r, rankCounts.get(r) + 1);
                sum += m;
                count++;
            }
        }

        double avg = (count <= 0) ? 1.00 : (sum / count);

        // clamp bezpieczeństwa
        if (avg < 0.10) avg = 0.10;
        if (avg > 10.00) avg = 10.00;

        snap.set(new Snapshot(avg, sum, count, rankCounts));
    }

    /** Globalny mnożnik cen serwerowych (średnia z próby). */
    public double getServerMultiplier() {
        return snap.get().avg;
    }

    /** Suma mnożników (czasem chcesz ją pokazać). */
    public double getSumMultiplier() {
        return snap.get().sum;
    }

    /** Ilu graczy weszło do próbki. */
    public int getSampleSize() {
        return snap.get().count;
    }

    /** Ile jest graczy danej rangi w próbce. */
    public int getRankCount(DolaryMultiplierService.Rank r) {
        return snap.get().rankCounts.getOrDefault(r, 0);
    }

    /** Cena bazowa -> cena dynamiczna GLOBALNA (dla wszystkich taka sama). */
    public long toServerDynamicPrice(long basePrice) {
        if (basePrice <= 0) return 0L;
        double m = getServerMultiplier();
        long out = Math.round(basePrice * m);
        return Math.max(0L, out);
    }

    /** Format 1.23 */
    public static String fmtMult(double m) {
        return String.format(Locale.ROOT, "%.2f", m);
    }

    private record Snapshot(
            double avg,
            double sum,
            int count,
            Map<DolaryMultiplierService.Rank, Integer> rankCounts
    ) {
        static Snapshot empty() {
            EnumMap<DolaryMultiplierService.Rank, Integer> map =
                    new EnumMap<>(DolaryMultiplierService.Rank.class);
            for (DolaryMultiplierService.Rank r : DolaryMultiplierService.Rank.values()) {
                map.put(r, 0);
            }
            return new Snapshot(1.00, 0.0, 0, map);
        }
    }
}