package net.minelink.ctplus.listener;

import dev.geco.gsit.api.event.PrePlayerCrawlEvent;
import net.minelink.ctplus.CombatTagPlus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class GSitListener implements Listener {

    private final CombatTagPlus plugin;

    public GSitListener(CombatTagPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCrawlStart(PrePlayerCrawlEvent event) {
        if(plugin.getTagManager().isTagged(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

}
