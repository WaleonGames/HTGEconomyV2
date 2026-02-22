package pl.htgmc.htgeconomy.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import pl.htgmc.htgeconomy.dolary.DolaryServerMultiplierService;

public final class EcoServerListener implements Listener {

    private final EcoServerGUI gui;
    private final DolaryServerMultiplierService serverMult;

    public EcoServerListener(EcoServerGUI gui, DolaryServerMultiplierService serverMult) {
        this.gui = gui;
        this.serverMult = serverMult;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null) return;
        if (!EcoServerGUI.TITLE.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        int slot = e.getRawSlot();

        if (slot == EcoServerGUI.SLOT_CLOSE) {
            p.closeInventory();
            return;
        }

        if (slot == EcoServerGUI.SLOT_REFRESH) {
            serverMult.recalcNow();
            p.openInventory(gui.buildFor(p));
            p.updateInventory();
        }
    }
}