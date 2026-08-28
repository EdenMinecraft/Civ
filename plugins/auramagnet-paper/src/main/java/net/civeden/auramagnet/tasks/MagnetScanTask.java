package net.civeden.auramagnet.tasks;

import java.util.Collection;
import net.civeden.auramagnet.AuraMagnet;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runs every 5 ticks: finds players holding a tagged Magnet Hoe (offhand always,
 * main hand if they've enabled that in /config - needed for Bedrock/Geyser players,
 * who have no offhand) and starts an ItemPullTask for each nearby dropped item they
 * have enabled in /config that isn't already being pulled.
 */
public class MagnetScanTask extends BukkitRunnable {

    public static final String PULLING_META_KEY = "aura_pulling";

    @Override
    public void run() {
        AuraMagnet plugin = AuraMagnet.getInstance();
        double radius = plugin.getMagnetRadius();
        double radiusSq = radius * radius;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isDead() || !player.isOnline()) {
                continue;
            }

            EquipmentSlot activeSlot = getActiveMagnetSlot(player, plugin);
            if (activeSlot == null) {
                continue;
            }

            Collection<Entity> nearby = player.getNearbyEntities(radius, radius, radius);
            for (Entity entity : nearby) {
                if (!(entity instanceof Item groundItem) || groundItem.isDead()) {
                    continue;
                }
                if (groundItem.hasMetadata(PULLING_META_KEY)) {
                    continue;
                }
                if (groundItem.getLocation().distanceSquared(player.getLocation()) > radiusSq) {
                    continue;
                }

                Material material = groundItem.getItemStack().getType();
                if (!plugin.getMagnetHoeSettings().isEnabled(player, material)) {
                    continue;
                }

                groundItem.setMetadata(PULLING_META_KEY, new FixedMetadataValue(plugin, true));
                groundItem.setGravity(false);
                groundItem.setPickupDelay(Integer.MAX_VALUE);

                new ItemPullTask(player, groundItem, activeSlot).runTaskTimer(plugin, 0L, 1L);
            }
        }
    }

    /**
     * @return The hand holding a tagged Magnet Hoe, preferring offhand, or null if
     * neither hand qualifies. Main hand only counts if the player has that setting
     * enabled - it's needed for Bedrock/Geyser players, who have no offhand slot.
     */
    private EquipmentSlot getActiveMagnetSlot(Player player, AuraMagnet plugin) {
        if (isAuraMagnetHoe(player.getInventory().getItemInOffHand())) {
            return EquipmentSlot.OFF_HAND;
        }
        if (plugin.getMagnetHoeSettings().isMainHandActivationEnabled(player)
            && isAuraMagnetHoe(player.getInventory().getItemInMainHand())) {
            return EquipmentSlot.HAND;
        }
        return null;
    }

    private boolean isAuraMagnetHoe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(AuraMagnet.MAGNET_HOE_KEY, PersistentDataType.BYTE);
    }
}
