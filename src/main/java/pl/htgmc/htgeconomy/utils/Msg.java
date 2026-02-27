package pl.htgmc.htgeconomy.utils;

import org.bukkit.command.CommandSender;

public final class Msg {
    private Msg() {}

    public static void info(CommandSender s, String msg) {
        s.sendMessage("§d§lAstracraft §f" + msg);
    }

    public static void err(CommandSender s, String msg) {
        s.sendMessage("§d§lAstracraft §c" + msg);
    }

    public static void ok(CommandSender s, String msg) {
        s.sendMessage("§d§lAstracraft §a" + msg);
    }

    public static void warn(CommandSender s, String msg) {
        s.sendMessage("§d§lAstracraft §e" + msg);
    }

    /** Wysyła dokładnie to, co podasz (bez prefixu). Idealne do list/ historii. */
    public static void raw(CommandSender s, String msg) {
        s.sendMessage(msg);
    }
}