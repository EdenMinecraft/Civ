package net.civeden.auramagnet.tasks;

import net.civeden.auramagnet.AuraMagnet;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Eases a single dropped item toward the player each tick until it's collected
 * or the pull is interrupted (hoe swapped out, player logs off/dies, item
 * despawns, or a 10 second safety valve trips).
 */
public class ItemPullTask extends BukkitRunnable {

    private static final double MIN_SPEED = 0.12;
    private static final double MAX_SPEED = 0.85;
    private static final double COLLECT_DISTANCE_SQ = 0.8 * 0.8;
    private static final int MAX_TICKS = 200;
    private static final int PARTICLE_INTERVAL = 2;

    private final Player player;
    private final Item item;
    private final EquipmentSlot handSlot;
    private int ticksAlive = 0;

    public ItemPullTask(Player player, Item item, EquipmentSlot handSlot) {
        this.player = player;
        this.item = item;
        this.handSlot = handSlot;
    }

    @Override
    public void run() {
        ticksAlive++;

        if (item.isDead() || !item.isValid() || !player.isOnline() || player.isDead()
            || !isStillHoldingMagnetHoe() || ticksAlive > MAX_TICKS) {
            cancelAndCleanup();
            return;
        }

        Location target = player.getLocation().add(0, 1.0, 0);
        Location current = item.getLocation();
        double distance = current.distance(target);

        if (distance * distance < COLLECT_DISTANCE_SQ) {
            collectItem();
            return;
        }

        double radius = AuraMagnet.getInstance().getMagnetRadius();
        double t = 1.0 - Math.min(distance / radius, 1.0);
        double speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * t;

        Vector direction = target.toVector().subtract(current.toVector()).normalize().multiply(speed);
        item.setVelocity(direction);

        if (ticksAlive % PARTICLE_INTERVAL == 0) {
            spawnTrailParticle(current, t);
        }
    }

    private void spawnTrailParticle(Location loc, double t) {
        int count = (int) (3 + t * 5);
        double spread = 0.20 - (t * 0.15);
        player.spawnParticle(Particle.ENCHANT, loc, count, spread, spread, spread, 0.05);
    }

    private void playCollectEffect() {
        Location chest = player.getLocation().add(0, 1.0, 0);
        player.spawnParticle(Particle.ENCHANTED_HIT, chest, 10, 0.2, 0.2, 0.2, 0.1);
        float pitch = 0.9f + (float) (Math.random() * 0.4f);
        player.playSound(chest, Sound.ENTITY_ITEM_PICKUP, 0.4f, pitch);
    }

    private void collectItem() {
        ItemStack stack = item.getItemStack();
        var leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            cancelAndCleanup();
            return;
        }

        item.remove();
        playCollectEffect();
        applyDurability();
        this.cancel();
    }

    private void applyDurability() {
        EntityEquipment equipment = player.getEquipment();
        ItemStack activeHoe = equipment.getItem(handSlot);
        if (activeHoe.getType().isAir()) {
            return;
        }

        ItemMeta meta = activeHoe.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        int cost = AuraMagnet.getInstance().getDurabilityCostPerItem();
        double chance = AuraMagnet.getInstance().getDurabilityChancePerItem();
        if (cost <= 0 || chance <= 0.0 || Math.random() >= chance) {
            return;
        }

        int maxDura = activeHoe.getType().getMaxDurability();
        int newDamage = damageable.getDamage() + cost;

        if (newDamage >= maxDura) {
            equipment.setItem(handSlot, null);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            damageable.setDamage(newDamage);
            activeHoe.setItemMeta(meta);
        }
    }

    private boolean isStillHoldingMagnetHoe() {
        ItemStack activeHoe = player.getEquipment().getItem(handSlot);
        if (activeHoe.getType().isAir()) {
            return false;
        }
        ItemMeta meta = activeHoe.getItemMeta();
        return meta != null
            && meta.getPersistentDataContainer().has(AuraMagnet.MAGNET_HOE_KEY, PersistentDataType.BYTE);
    }

    private void cancelAndCleanup() {
        if (!item.isDead() && item.isValid()) {
            item.setGravity(true);
            item.setPickupDelay(10);
            item.setVelocity(new Vector(0, 0, 0));
            item.removeMetadata(MagnetScanTask.PULLING_META_KEY, AuraMagnet.getInstance());
        }
        this.cancel();
    }
}
