package pl.htgmc.htgeconomy.dolary;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

public final class VaultHook {

    private final Plugin plugin;

    public VaultHook(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean register(DolaryService dolary) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault nie znaleziony — dolary nie będą dostępne przez Vault.");
            return false;
        }

        plugin.getServer().getServicesManager().register(
                Economy.class,
                new VaultEconomyProvider(dolary),
                plugin,
                ServicePriority.Highest
        );

        plugin.getLogger().info("Zarejestrowano Vault Economy dla waluty: dolary");
        return true;
    }
}