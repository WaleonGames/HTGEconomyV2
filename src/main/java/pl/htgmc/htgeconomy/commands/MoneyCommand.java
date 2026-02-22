package pl.htgmc.htgeconomy.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.htgmc.htgeconomy.utils.MoneyUtil;
import pl.htgmc.htgeconomy.utils.Msg;

import java.util.*;

public final class MoneyCommand implements CommandExecutor, TabCompleter {

    public interface Backend {
        long get(UUID uuid);
        void set(UUID uuid, long bal);
        void add(UUID uuid, long amount);
        boolean take(UUID uuid, long amount);
        boolean pay(UUID from, UUID to, long amount);
    }

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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // /dolary
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                Msg.err(sender, "Ta komenda jest dla gracza: /" + label + " <...>");
                return true;
            }
            long bal = backend.get(p.getUniqueId());
            Msg.info(sender, "Twoje saldo: §e" + MoneyUtil.fmt(bal, symbol));
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

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) {
                Msg.err(sender, "Nie znaleziono gracza.");
                return true;
            }

            Long amount = MoneyUtil.parseAmount(args[2]);
            if (amount == null || amount <= 0) {
                Msg.err(sender, "Podaj poprawną kwotę (liczba > 0).");
                return true;
            }

            boolean ok = backend.pay(p.getUniqueId(), target.getUniqueId(), amount);
            if (!ok) {
                Msg.err(sender, "Nie masz wystarczających środków.");
                return true;
            }

            Msg.ok(sender, "Wysłano §e" + MoneyUtil.fmt(amount, symbol) + "§a do §f" + (target.getName() != null ? target.getName() : args[1]));
            if (target.isOnline()) {
                Player tp = target.getPlayer();
                if (tp != null) Msg.ok(tp, "Otrzymałeś §e" + MoneyUtil.fmt(amount, symbol) + "§a od §f" + p.getName());
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

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) {
                Msg.err(sender, "Nie znaleziono gracza.");
                return true;
            }

            Long amount = MoneyUtil.parseAmount(args[2]);
            if (amount == null) {
                Msg.err(sender, "Podaj poprawną kwotę (liczba >= 0).");
                return true;
            }

            UUID tu = target.getUniqueId();

            switch (sub) {
                case "add" -> {
                    if (amount <= 0) {
                        Msg.err(sender, "Kwota musi być > 0.");
                        return true;
                    }
                    backend.add(tu, amount);
                    Msg.ok(sender, "Dodano §e" + MoneyUtil.fmt(amount, symbol) + "§a dla §f" + (target.getName() != null ? target.getName() : args[1]));
                }
                case "set" -> {
                    backend.set(tu, Math.max(0L, amount));
                    Msg.ok(sender, "Ustawiono saldo §f" + (target.getName() != null ? target.getName() : args[1]) + "§a na §e" + MoneyUtil.fmt(amount, symbol));
                }
                case "take" -> {
                    if (amount <= 0) {
                        Msg.err(sender, "Kwota musi być > 0.");
                        return true;
                    }
                    boolean ok = backend.take(tu, amount);
                    if (!ok) {
                        Msg.err(sender, "Gracz ma za mało środków.");
                        return true;
                    }
                    Msg.ok(sender, "Zabrano §e" + MoneyUtil.fmt(amount, symbol) + "§a graczowi §f" + (target.getName() != null ? target.getName() : args[1]));
                }
            }
            return true;
        }

        Msg.err(sender,
                "Użycie: /" + label +
                        " | /" + label + " pay <nick> <kwota>" +
                        " | /" + label + " add|set|take <nick> <kwota>");
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
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, args[1]);
            }
        }

        return List.of();
    }

    private static List<String> filter(List<String> list, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        ArrayList<String> out = new ArrayList<>();
        for (String s : list) if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        return out;
    }
}