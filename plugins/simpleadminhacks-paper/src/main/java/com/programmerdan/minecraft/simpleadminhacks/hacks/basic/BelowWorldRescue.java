package com.programmerdan.minecraft.simpleadminhacks.hacks.basic;

import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.framework.BasicHack;
import com.programmerdan.minecraft.simpleadminhacks.framework.BasicHackConfig;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

public final class BelowWorldRescue extends BasicHack {

    private static final int RESCUE_DEPTH = 10;

    public BelowWorldRescue(final SimpleAdminHacks plugin, final BasicHackConfig config) {
        super(plugin, config);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        final Location to = event.getTo();
        final World world = to.getWorld();
        if (to.getY() >= world.getMinHeight() - RESCUE_DEPTH) {
            return;
        }

        final Location surface = to.clone();
        surface.setY(world.getHighestBlockYAt(to, HeightMap.WORLD_SURFACE) + 2);

        final Entity vehicle = player.getVehicle();
        if (vehicle != null && teleportSafely(surface, vehicle, TeleportFlag.EntityState.RETAIN_PASSENGERS)) {
            // Player rode along with the vehicle; just clear their own fall state too
            player.setVelocity(new Vector());
            player.setFallDistance(0);
            return;
        }
        teleportSafely(surface, player);
    }

    private boolean teleportSafely(final Location loc, final Entity entity, final TeleportFlag... flags) {
        if (entity.teleport(loc, flags)) {
            entity.setVelocity(new Vector());
            entity.setFallDistance(0);
            return true;
        }
        return false;
    }

}
