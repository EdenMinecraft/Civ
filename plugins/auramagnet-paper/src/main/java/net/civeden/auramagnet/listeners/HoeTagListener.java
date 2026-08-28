package net.civeden.auramagnet.listeners;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.civeden.auramagnet.AuraMagnet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Crafted hoes only carry an identifying lore line, since the crafting factory
 * can't attach persistent data directly. This listener catches the first time a
 * player touches such a hoe and stamps a PDC tag onto it, so the scan task never
 * has to read lore on the hot path.
 */
public class HoeTagListener implements Listener {

    private static final Set<Material> HOE_MATERIALS = EnumSet.of(
        Material.WOODEN_HOE,
        Material.STONE_HOE,
        Material.IRON_HOE,
        Material.GOLDEN_HOE,
        Material.DIAMOND_HOE,
        Material.NETHERITE_HOE
    );

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        tryInject(event.getPlayer().getInventory().getItem(event.getNewSlot()));
        tryInject(event.getPlayer().getInventory().getItemInOffHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        tryInject(event.getOffHandItem());
        tryInject(event.getMainHandItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        tryInject(event.getCurrentItem());
        tryInject(event.getCursor());
    }

    public static void tryInject(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !HOE_MATERIALS.contains(item.getType())) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (meta.getPersistentDataContainer().has(AuraMagnet.MAGNET_HOE_KEY, PersistentDataType.BYTE)) {
            return;
        }

        if (!hasMatchingLore(meta)) {
            return;
        }

        meta.getPersistentDataContainer().set(AuraMagnet.MAGNET_HOE_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private static boolean hasMatchingLore(ItemMeta meta) {
        if (!meta.hasLore()) {
            return false;
        }
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return false;
        }

        Component trigger = LegacyComponentSerializer.legacyAmpersand()
            .deserialize(AuraMagnet.getInstance().getMagnetLoreTrigger());
        String triggerPlain = PlainTextComponentSerializer.plainText().serialize(trigger);

        for (Component line : lore) {
            if (PlainTextComponentSerializer.plainText().serialize(line).equalsIgnoreCase(triggerPlain)) {
                return true;
            }
        }
        return false;
    }
}
