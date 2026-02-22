package pl.htgmc.htgeconomy.utils;

import org.bukkit.command.CommandSender;

public final class Msg {
    private Msg() {}

    public static void info(CommandSender s, String msg) {
        s.sendMessage("§8[§aEco§8] §f" + msg);
    }

    public static void err(CommandSender s, String msg) {
        s.sendMessage("§8[§cEco§8] §c" + msg);
    }

    public static void ok(CommandSender s, String msg) {
        s.sendMessage("§8[§aEco§8] §a" + msg);
    }

    public static void warn(CommandSender s, String msg) {
        s.sendMessage("§8[§eEco§8] §e" + msg);
    }

    /** Wysyła dokładnie to, co podasz (bez prefixu). Idealne do list/ historii. */
    public static void raw(CommandSender s, String msg) {
        s.sendMessage(msg);
    }
}