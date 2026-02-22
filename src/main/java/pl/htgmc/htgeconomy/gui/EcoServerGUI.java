package pl.htgmc.htgeconomy.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.htgmc.htgeconomy.dolary.DolaryMultiplierService;
import pl.htgmc.htgeconomy.dolary.DolaryServerMultiplierService;
import pl.htgmc.htgeconomy.storage.JsonBalanceStorage;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

public final class EcoServerGUI {

    public static final String TITLE = "§a§lHTGEconomy §8— §fSerwer";
    public static final int SIZE = 27;

    // sloty
    public static final int SLOT_DOLARY  = 11;
    public static final int SLOT_INFO    = 13;
    public static final int SLOT_VPLN    = 15;
    public static final int SLOT_REFRESH = 22;
    public static final int SLOT_CLOSE   = 26;

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

    private final JsonBalanceStorage dolaryStore;
    private final JsonBalanceStorage vplnStore;
    private final DolaryMultiplierService mult;
    private final DolaryServerMultiplierService serverMult;

    public EcoServerGUI(JsonBalanceStorage dolaryStore,
                        JsonBalanceStorage vplnStore,
                        DolaryMultiplierService mult,
                        DolaryServerMultiplierService serverMult) {
        this.dolaryStore = dolaryStore;
        this.vplnStore = vplnStore;
        this.mult = mult;
        this.serverMult = serverMult;
    }

    public Inventory buildFor(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        fillGlass(inv);

        Stats dStats = computeStats(dolaryStore.snapshot());
        Stats vStats = computeStats(vplnStore.snapshot());

        inv.setItem(SLOT_DOLARY, makeMoneyItem(
                Material.GOLD_INGOT,
                "§e§lDOLARY §7(serwer)",
                "$",
                dStats
        ));

        inv.setItem(SLOT_VPLN, makeMoneyItem(
                Material.EMERALD,
                "§a§lVPLN §7(serwer)",
                "VPLN",
                vStats
        ));

        inv.setItem(SLOT_INFO, makeInfoItem(viewer));
        inv.setItem(SLOT_REFRESH, makeButton(Material.SUNFLOWER, "§b§lOdśwież", "§7Kliknij, aby przeliczyć dane ponownie."));
        inv.setItem(SLOT_CLOSE, makeButton(Material.BARRIER, "§c§lZamknij", "§7Kliknij, aby zamknąć panel."));

        return inv;
    }

    private ItemStack makeInfoItem(Player viewer) {
        var rank = mult.getRank(viewer);
        double playerM = mult.getMultiplier(viewer);

        double sum = serverMult.getSumMultiplier();
        int n = serverMult.getSampleSize();
        double avg = serverMult.getServerMultiplier();

        int nDefault = serverMult.getRankCount(DolaryMultiplierService.Rank.DEFAULT);
        int nVip     = serverMult.getRankCount(DolaryMultiplierService.Rank.VIP);
        int nMvip    = serverMult.getRankCount(DolaryMultiplierService.Rank.MVIP);
        int nAstra   = serverMult.getRankCount(DolaryMultiplierService.Rank.ASTRA);

        long base1 = 100;
        long base2 = 1000;
        long base3 = 10000;

        long dyn1 = serverMult.toServerDynamicPrice(base1);
        long dyn2 = serverMult.toServerDynamicPrice(base2);
        long dyn3 = serverMult.toServerDynamicPrice(base3);

        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§f§lMnożniki & Ceny (GLOBAL)");

        List<String> lore = new ArrayList<>();

        lore.add("§f● §7Twoja ranga: §e" + rank.key);
        lore.add("§f● §7Twój mnożnik $: §e" + String.format(Locale.ROOT, "%.2f", playerM) + "x");
        lore.add("");

        // Wskazówka o próbie – bezpiecznie, nawet jeśli zmienisz tryb liczenia
        lore.add("§b§lOgólny mnożnik serwera (PRÓBA):");
        lore.add("§f● §7Suma mnożników: §e" + String.format(Locale.ROOT, "%.2f", sum));
        lore.add("§f● §7Liczba osób: §e" + n);
        lore.add("§f● §7GLOBAL (średnia): §e" + String.format(Locale.ROOT, "%.2f", avg) + "x");
        lore.add("");

        lore.add("§6§lSkład rang (PRÓBA):");
        lore.add("§7- §fdefault: §e" + nDefault);
        lore.add("§7- §fvip: §e" + nVip);
        lore.add("§7- §fmvip: §e" + nMvip);
        lore.add("§7- §fastra: §e" + nAstra);
        lore.add("");

        lore.add("§b§lCena bazowa -> cena dynamiczna (GLOBAL):");
        lore.add("§7• §f" + fmt(base1)  + " §8-> §e" + fmt(dyn1) + " §7$");
        lore.add("§7• §f" + fmt(base2)  + " §8-> §e" + fmt(dyn2) + " §7$");
        lore.add("§7• §f" + fmt(base3)  + " §8-> §e" + fmt(dyn3) + " §7$");
        lore.add("§8dynamiczna = bazowa × globalny_mnożnik");

        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack makeMoneyItem(Material mat, String title, String currency, Stats s) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(title);

        List<String> lore = new ArrayList<>();
        lore.add("§f● §7Suma: §e" + fmt(s.total) + " §7" + currency);
        lore.add("§f● §7Średnia: §e" + fmt(s.avg) + " §7" + currency);
        lore.add("§f● §7Kont: §e" + s.accounts);
        lore.add("");

        if (!s.top.isEmpty()) {
            lore.add("§6§lTop 3:");
            for (int i = 0; i < s.top.size(); i++) {
                TopEntry e = s.top.get(i);
                lore.add("§7" + (i + 1) + ". §f" + e.name + " §8— §e" + fmt(e.balance) + " §7" + currency);
            }
        } else {
            lore.add("§8Brak danych do topki.");
        }

        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack makeButton(Material mat, String name, String loreLine) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(loreLine));
        it.setItemMeta(meta);
        return it;
    }

    private static void fillGlass(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }
    }

    private static String fmt(long value) {
        return MONEY_FMT.get().format((double) value);
    }

    private static Stats computeStats(Map<UUID, Long> map) {
        if (map == null || map.isEmpty()) return Stats.empty();

        long total = 0;
        long count = 0;

        for (Long v : map.values()) {
            if (v == null) continue;
            long val = Math.max(0, v);
            total += val;
            count++;
        }

        long avg = (count <= 0) ? 0 : (total / count);

        List<Map.Entry<UUID, Long>> sorted = map.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .collect(Collectors.toList());

        List<TopEntry> top = new ArrayList<>();
        for (var e : sorted) {
            UUID uuid = e.getKey();
            long bal = Math.max(0, e.getValue());
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = (op != null && op.getName() != null) ? op.getName() : uuid.toString().substring(0, 8);
            top.add(new TopEntry(name, bal));
        }

        return new Stats(total, avg, count, top);
    }

    private record TopEntry(String name, long balance) {}
    private record Stats(long total, long avg, long accounts, List<TopEntry> top) {
        static Stats empty() { return new Stats(0, 0, 0, List.of()); }
    }
}