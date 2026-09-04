package com.programmerdan.minecraft.simpleadminhacks.hacks;


import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.configs.BetterRailsConfig;
import com.programmerdan.minecraft.simpleadminhacks.framework.SimpleHack;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.Set;

public final class BetterRails extends SimpleHack<BetterRailsConfig> implements Listener {

    // A minecart goes at 8m/s but its internal speed is 0.4, this adjusts for that
    private static final double METRES_PER_SECOND_TO_SPEED = 0.05;
    private static final double VANILLA_SPEED = 0.4;

    // List valid blocks which dont block rail speed boost
    private static final Set<Material> VALID_SPEED_BLOCKS = Set.of(
        Material.GLASS,
        Material.BLACK_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.BROWN_STAINED_GLASS,
        Material.CYAN_STAINED_GLASS, Material.GRAY_STAINED_GLASS, Material.GREEN_STAINED_GLASS,
        Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS, Material.LIME_STAINED_GLASS,
        Material.MAGENTA_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.PINK_STAINED_GLASS,
        Material.PURPLE_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.WHITE_STAINED_GLASS,
        Material.YELLOW_STAINED_GLASS, Material.TINTED_GLASS, Material.WATER, Material.AIR, Material.CAVE_AIR
    );

    public BetterRails(SimpleAdminHacks plugin, final BetterRailsConfig config) {
        super(plugin, config);
    }

    public static BetterRailsConfig generate(SimpleAdminHacks plugin, ConfigurationSection config) {
        return new BetterRailsConfig(plugin, config);
    }

    @Override
    public void onEnable() {
        plugin().registerListener(this);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void on(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) {
            return;
        }

        if (minecart instanceof PoweredMinecart) {
            return;
        }

        Location to = event.getTo();
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }

        for (Entity entity : minecart.getPassengers()) {
            if (entity instanceof Player) {
                adjustSpeed(minecart);
                return;
            }
        }
    }

    @EventHandler
    public void on(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) {
            return;
        }

        if (minecart instanceof PoweredMinecart) {
            return;
        }

        if (event.getEntered() instanceof Player) {
            adjustSpeed(minecart);
        }
    }

    @EventHandler
    public void on(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) {
            return;
        }

        // Empty minecarts should return to their vanilla speed
        minecart.setMaxSpeed(VANILLA_SPEED);
    }

    private void adjustSpeed(Minecart minecart) {
        Location loc = minecart.getLocation();

        Material belowRail = loc.clone().subtract(0, 1, 0).getBlock().getType();
        Material belowRail2 = loc.clone().subtract(0, 2, 0).getBlock().getType();

        double speedMetresPerSecond;

        if (hasOpenSky(loc)) {
            speedMetresPerSecond = maxOrGet(
                config.getSkySpeedMetresPerSecond(belowRail),
                config.getSkySpeedMetresPerSecond(belowRail2),
                config.getSkySpeed()
            );
        } else {
            speedMetresPerSecond = maxOrGet(
                config.getMaxSpeedMetresPerSecond(belowRail),
                config.getMaxSpeedMetresPerSecond(belowRail2),
                config.getBaseSpeed()
            );
        }

        minecart.setMaxSpeed(speedMetresPerSecond * METRES_PER_SECOND_TO_SPEED);
    }

    private boolean hasOpenSky(Location loc) {
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int y = loc.getBlockY();

        for (int i = y + 1; i < loc.getWorld().getMaxHeight(); i++) {
            Material mat = loc.getWorld().getBlockAt(x, i, z).getType();
            if (VALID_SPEED_BLOCKS.contains(mat)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private double maxOrGet(Double left, Double right, double defaultAmount) {
        if (left != null && right != null) {
            return Math.max(left, right);
        } else if (left != null) {
            return left;
        } else if (right != null) {
            return right;
        } else {
            return defaultAmount;
        }
    }
}
