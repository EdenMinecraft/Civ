package vg.civcraft.mc.civchat2.listeners;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import vg.civcraft.mc.civchat2.database.CivChatDAO;
import vg.civcraft.mc.civchat2.utility.CivChat2Config;
import vg.civcraft.mc.civchat2.utility.CivChat2SettingsManager;
import vg.civcraft.mc.civmodcore.inventory.items.ItemUtils;
import vg.civcraft.mc.civmodcore.inventory.items.MaterialUtils;

public class KillListener implements Listener {

    private CivChat2SettingsManager settingsMan;
    private CivChat2Config config;
    private CivChatDAO dao;

    public KillListener(CivChat2Config config, CivChatDAO dao, CivChat2SettingsManager settingsMan) {
        this.config = config;
        this.dao = dao;
        this.settingsMan = settingsMan;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim.getKiller() == null) {
            return;
        }
        int killBroadcastRange = config.getKillBroadcastRange();
        if (killBroadcastRange <= 0) {
            return;
        }
        Player killer = victim.getKiller();
        if (!settingsMan.getSendOwnKills(killer.getUniqueId())) {
            return;
        }
        ItemStack item = killer.getInventory().getItemInMainHand();
        Component killerComponent = Component.text(killer.getDisplayName(), NamedTextColor.DARK_GRAY);
        Component victimComponent = Component.text(victim.getDisplayName(), NamedTextColor.DARK_GRAY);
        Component verb = Component.text("killed", NamedTextColor.DARK_GRAY);
        Component accent = Component.text("> ", NamedTextColor.DARK_GRAY).decorate(TextDecoration.BOLD);
        Component killMessage;
        if (item == null || MaterialUtils.isAir(item.getType())) {
            killMessage = Component.text()
                .append(accent)
                .append(killerComponent)
                .append(Component.space())
                .append(verb)
                .append(Component.space())
                .append(victimComponent)
                .append(Component.space())
                .append(Component.text("by hand", NamedTextColor.DARK_GRAY))
                .build();
        } else {
            Boolean hasWordBank = Optional.ofNullable(ItemUtils.getItemMeta(item))
                .map(r -> r.displayName())
                .map(f -> f.children().size() > 0)
                .orElse(false);

            Component itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName()
                : Component.translatable(item.getType());

            String connector;
            if (!hasWordBank) {
                connector = "with";
            } else {
                connector = settingsMan.getKillMessageFormat(killer.getUniqueId()).simpleDescription;
            }

            Component itemPhrase;
            if (connector.isBlank()) {
                itemPhrase = itemName;
            } else {
                itemPhrase = Component.text(connector + " ")
                    .append(itemName);
            }
            itemPhrase = itemPhrase.color(NamedTextColor.DARK_GRAY)
                .hoverEvent(item.asHoverEvent());

            killMessage = Component.text()
                .append(accent)
                .append(killerComponent)
                .append(Component.space())
                .append(verb)
                .append(Component.space())
                .append(victimComponent)
                .append(Component.space())
                .append(itemPhrase)
                .build();
        }

        Location killLoc = victim.getLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();
            if (!loc.getWorld().equals(killLoc.getWorld())) {
                continue;
            }
            if (loc.distance(killLoc) > killBroadcastRange) {
                continue;
            }
            if (!settingsMan.getReceiveKills(p.getUniqueId())) {
                continue;
            }
            if (!settingsMan.getReceiveKillsFromIgnored(p.getUniqueId())
                && dao.isIgnoringPlayer(p.getUniqueId(), killer.getUniqueId())) {
                continue;
            }
            p.sendMessage(killMessage);
        }
    }

}
