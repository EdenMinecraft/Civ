package com.programmerdan.minecraft.simpleadminhacks.hacks;

import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.configs.HorseStatsConfig;
import com.programmerdan.minecraft.simpleadminhacks.framework.SimpleHack;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Strider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class HorseStats extends SimpleHack<HorseStatsConfig> implements Listener {

    // Wiki horse top speed (14.57 b/s) divided by max movement-speed attribute (0.3375)
    private static final double INTERNAL_TO_METRES_PER_SECOND = 43.17037037037037;
    private static final double JUMP_GRAVITY = 0.08;
    private static final double JUMP_DRAG = 0.98;

    public HorseStats(SimpleAdminHacks plugin, HorseStatsConfig config) {
        super(plugin, config);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHorseStatCheck(PlayerInteractEntityEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!item.getType().equals(config.getHorseCheckerItem())) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (entity instanceof AbstractHorse) {
            AbstractHorse horse = (AbstractHorse) entity;
            AttributeInstance attrHealth = horse.getAttribute(Attribute.MAX_HEALTH);
            AttributeInstance attrSpeed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            event.getPlayer().sendMessage(String.format("%sHealth = %f, Speed = %fm/s, Jump height = %f blocks",
                ChatColor.YELLOW,
                attrHealth.getValue(),
                attrSpeed.getValue() * INTERNAL_TO_METRES_PER_SECOND,
                jumpHeightInBlocks(horse.getAttribute(Attribute.JUMP_STRENGTH).getValue())));
            event.setCancelled(true);
        } else if (entity instanceof Strider) {
            Strider strider = (Strider) entity;
            AttributeInstance attrHealth = strider.getAttribute(Attribute.MAX_HEALTH);
            AttributeInstance attrSpeed = strider.getAttribute(Attribute.MOVEMENT_SPEED);
            event.getPlayer().sendMessage(String.format("%sHealth = %f, Speed = %fm/s",
                ChatColor.YELLOW,
                attrHealth.getValue(),
                attrSpeed.getValue() * INTERNAL_TO_METRES_PER_SECOND));
            event.setCancelled(true);
        } else {
            return;
        }
    }

    // Simulate the actual jump: each tick the entity rises by its velocity, then
    // velocity drops by gravity and is scaled by drag. Peak height is the sum of
    // the upward velocities. The jump strength attribute is the initial velocity.
    private double jumpHeightInBlocks(double jumpStrength) {
        double height = 0.0;
        double velocity = jumpStrength;
        while (velocity > 0) {
            height += velocity;
            velocity = (velocity - JUMP_GRAVITY) * JUMP_DRAG;
        }
        return height;
    }

    @Override
    public void registerListeners() {
        if (config.isEnabled()) {
            plugin().log("Registering HorseStats listeners");
            plugin().registerListener(this);
        }
    }

    @Override
    public void registerCommands() {

    }

    @Override
    public void dataBootstrap() {

    }

    @Override
    public void unregisterListeners() {

    }

    @Override
    public void unregisterCommands() {

    }

    @Override
    public void dataCleanup() {

    }

    public static HorseStatsConfig generate(SimpleAdminHacks plugin, ConfigurationSection config) {
        return new HorseStatsConfig(plugin, config);
    }

    @Override
    public String status() {
        return config.isEnabled() ? "HorseStats enabled." : "HorseStats disabled.";
    }
}
