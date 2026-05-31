package pl.htgmc.htgeconomy.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.htgmc.htgeconomy.utils.Msg;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public final class MoneyCommand implements CommandExecutor, TabCompleter {

    public interface Backend {
        double get(UUID uuid);
        void set(UUID uuid, double bal);
        void add(UUID uuid, double amount);
        boolean take(UUID uuid, double amount);
        boolean pay(UUID from, UUID to, double amount);
    }

    private static final ThreadLocal<DecimalFormat> MONEY_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');

        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        df.setGroupingUsed(true);
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(2);
        return df;
    });

    private final String currencyKey;   // "dolary" / "vpln"
    private final String symbol;        // "$" / "VPLN"
    private final Backend backend;

    public MoneyCommand(String currencyKey, String symbol, Backend backend) {
        this.currencyKey = currencyKey;
        this.symbol = symbol;
        this.backend = backend;
    }

    private String perm(String node) {
        return "htgeco." + currencyKey + "." + node;
    }

    private String formatMoney(double amount) {
        return MONEY_FORMAT.get().format(round2(amount)) + symbol;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Double parseDecimalAmount(String input) {
        if (input == null) return null;

        String normalized = input.trim()
                .replace(" ", "")
                .replace(",", ".");

        if (normalized.isBlank()) return null;

        try {
            double value = Double.parseDouble(normalized);
            if (!Double.isFinite(value) || value < 0.0) return null;
            return round2(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // /dolary
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                Msg.err(sender, "Ta komenda jest dla gracza: /" + label + " <...>");
                return true;
            }

            double bal = backend.get(p.getUniqueId());
            Msg.info(sender, "Twoje saldo: §e" + formatMoney(bal));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // /dolary pay <nick> <kwota>
        if (sub.equals("pay")) {
            if (!(sender instanceof Player p)) {
                Msg.err(sender, "Tylko gracz może użyć pay.");
                return true;
            }

            if (!sender.hasPermission(perm("pay"))) {
                Msg.err(sender, "Brak uprawnień: " + perm("pay"));
                return true;
            }

            if (args.length < 3) {
                Msg.err(sender, "Użycie: /" + label + " pay <nick> <kwota>");
                return true;
            }

            String targetName = args[1];

            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            OfflinePlayer targetOffline = (onlineTarget != null)
                    ? onlineTarget
                    : Bukkit.getOfflinePlayer(targetName);

            UUID targetUuid = targetOffline.getUniqueId();
            if (targetUuid == null) {
                Msg.err(sender, "Nie znaleziono gracza.");
                return true;
            }

            Double amount = parseDecimalAmount(args[2]);
            if (amount == null || amount <= 0.0) {
                Msg.err(sender, "Podaj poprawną kwotę (liczba > 0).");
                return true;
            }

            if (p.getUniqueId().equals(targetUuid)) {
                Msg.err(sender, "Nie możesz wysłać pieniędzy sam do siebie.");
                return true;
            }

            boolean ok = backend.pay(p.getUniqueId(), targetUuid, amount);
            if (!ok) {
                Msg.err(sender, "Nie masz wystarczających środków.");
                return true;
            }

            String shownTargetName = (onlineTarget != null)
                    ? onlineTarget.getName()
                    : (targetOffline.getName() != null ? targetOffline.getName() : targetName);

            Msg.ok(sender, "Wysłano §e" + formatMoney(amount) + "§a do §f" + shownTargetName);

            if (onlineTarget != null) {
                Msg.ok(onlineTarget, "Otrzymałeś §e" + formatMoney(amount) + "§a od §f" + p.getName());
            }

            return true;
        }

        // admin: add/set/take
        if (sub.equals("add") || sub.equals("set") || sub.equals("take")) {
            if (!sender.hasPermission(perm("admin"))) {
                Msg.err(sender, "Brak uprawnień: " + perm("admin"));
                return true;
            }

            if (args.length < 3) {
                Msg.err(sender, "Użycie: /" + label + " " + sub + " <nick> <kwota>");
                return true;
            }

            String targetName = args[1];
            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            OfflinePlayer target = (onlineTarget != null) ? onlineTarget : Bukkit.getOfflinePlayer(targetName);

            UUID tu = target.getUniqueId();
            if (tu == null) {
                Msg.err(sender, "Nie znaleziono gracza.");
                return true;
            }

            Double amount = parseDecimalAmount(args[2]);
            if (amount == null) {
                Msg.err(sender, "Podaj poprawną kwotę (liczba >= 0).");
                return true;
            }

            String shownTargetName = (onlineTarget != null)
                    ? onlineTarget.getName()
                    : (target.getName() != null ? target.getName() : targetName);

            switch (sub) {
                case "add" -> {
                    if (amount <= 0.0) {
                        Msg.err(sender, "Kwota musi być > 0.");
                        return true;
                    }

                    backend.add(tu, amount);
                    Msg.ok(sender, "Dodano §e" + formatMoney(amount) + "§a dla §f" + shownTargetName);

                    if (onlineTarget != null) {
                        Msg.info(onlineTarget, "Dodano Ci §e" + formatMoney(amount) + "§7 (admin)");
                    }
                }

                case "set" -> {
                    double targetAmount = Math.max(0.0, amount);
                    backend.set(tu, targetAmount);

                    Msg.ok(sender, "Ustawiono saldo §f" + shownTargetName + "§a na §e" + formatMoney(targetAmount));

                    if (onlineTarget != null) {
                        Msg.info(onlineTarget, "Twoje saldo zostało ustawione na §e" + formatMoney(targetAmount) + "§7 (admin)");
                    }
                }

                case "take" -> {
                    if (amount <= 0.0) {
                        Msg.err(sender, "Kwota musi być > 0.");
                        return true;
                    }

                    boolean ok = backend.take(tu, amount);
                    if (!ok) {
                        Msg.err(sender, "Gracz ma za mało środków.");
                        return true;
                    }

                    Msg.ok(sender, "Zabrano §e" + formatMoney(amount) + "§a graczowi §f" + shownTargetName);

                    if (onlineTarget != null) {
                        Msg.info(onlineTarget, "Zabrano Ci §e" + formatMoney(amount) + "§7 (admin)");
                    }
                }
            }

            return true;
        }

        Msg.err(
                sender,
                "Użycie: /" + label +
                        " | /" + label + " pay <nick> <kwota>" +
                        " | /" + label + " add|set|take <nick> <kwota>"
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("pay");

            if (s.hasPermission(perm("admin"))) {
                base.add("add");
                base.add("set");
                base.add("take");
            }

            return filter(base, args[0]);
        }

        if (args.length == 2) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            boolean needsName = a0.equals("pay") || a0.equals("add") || a0.equals("set") || a0.equals("take");

            if (needsName) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return filter(names, args[1]);
            }
        }

        return List.of();
    }

    private static List<String> filter(List<String> list, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        ArrayList<String> out = new ArrayList<>();

        for (String s : list) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(s);
            }
        }

        return out;
    }
}