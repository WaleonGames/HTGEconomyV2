package pl.htgmc.htgeconomy.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import pl.htgmc.htgeconomy.HTGEconomyPlugin;
import pl.htgmc.htgeconomy.dolary.DolaryMultiplierService;
import pl.htgmc.htgeconomy.dolary.DolaryServerMultiplierService;
import pl.htgmc.htgeconomy.dolary.DolaryService;
import pl.htgmc.htgeconomy.vpln.VplnService;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

public final class EconomyExpansion extends PlaceholderExpansion {

    private final HTGEconomyPlugin plugin;
    private final DolaryService dolary;
    private final VplnService vpln;
    private final DolaryMultiplierService mult;
    private final DolaryServerMultiplierService serverMult;

    // Format PL: 12.345,67 (kropki tysiące, przecinek dziesiętne)
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

    public EconomyExpansion(HTGEconomyPlugin plugin,
                            DolaryService dolary,
                            VplnService vpln,
                            DolaryMultiplierService mult,
                            DolaryServerMultiplierService serverMult) {
        this.plugin = plugin;
        this.dolary = dolary;
        this.vpln = vpln;
        this.mult = mult;
        this.serverMult = serverMult;
    }

    @Override public String getIdentifier() { return "htgeco"; }
    @Override public String getAuthor() { return "ToJaWGYT"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player p, String params) {
        if (p == null || params == null) return "";

        final String key = params.toLowerCase(Locale.ROOT);
        final UUID uuid = p.getUniqueId();

        final long d = dolary.get(uuid);
        final long v = vpln.get(uuid);

        return switch (key) {

            // --- DOLARY ---
            case "dolary", "dolary_balance", "balance" -> String.valueOf(d);
            case "dolary_fmt", "dolary_balance_formatted", "balance_formatted" -> fmtMoney(d);

            // --- VPLN ---
            case "vpln", "vpln_balance" -> String.valueOf(v);
            case "vpln_fmt", "vpln_balance_formatted" -> fmtMoney(v);

            // --- MNOŻNIKI (GRACZ) ---
            case "multiplier" -> fmtMult(mult.getMultiplier(p));               // np. 1.10
            case "multiplier_raw" -> String.valueOf(mult.getMultiplier(p));    // np. 1.1
            case "rank" -> mult.getRank(p).key;                                  // default/vip/mvip/astra

            // --- MNOŻNIK (SERWER / GLOBALNY) ---
            case "server_multiplier" -> fmtMult(serverMult.getServerMultiplier());        // średnia z online
            case "server_multiplier_raw" -> String.valueOf(serverMult.getServerMultiplier());
            case "server_multiplier_sum" -> fmtMult(serverMult.getSumMultiplier());       // suma mnożników
            case "server_multiplier_n" -> String.valueOf(serverMult.getSampleSize());     // ilu online liczyło

            // --- WALUTA JAKO TEKST ---
            case "currency_dolary" -> "$";
            case "currency_vpln" -> "VPLN";

            default -> null;
        };
    }

    private static String fmtMoney(long value) {
        // long -> zawsze będzie ",00"
        return MONEY_FMT.get().format((double) value);
    }

    private static String fmtMult(double m) {
        // mnożnik jako 1.10 (kropka, bo to współczynnik)
        return String.format(Locale.ROOT, "%.2f", m);
    }
}