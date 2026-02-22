package pl.htgmc.htgeconomy.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.htgmc.htgeconomy.gui.EcoServerGUI;

public final class EcoServerCommand implements CommandExecutor {

    private final EcoServerGUI gui;

    public EcoServerCommand(EcoServerGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko gracz może użyć tej komendy.");
            return true;
        }

        p.openInventory(gui.buildFor(p));
        return true;
    }
}