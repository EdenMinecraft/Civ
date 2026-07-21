package com.github.longboyy.repelshitters;

import io.papermc.paper.event.entity.EntityMoveEvent;
import isaac.bastion.Bastion;
import isaac.bastion.BastionBlock;
import isaac.bastion.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import vg.civcraft.mc.namelayer.NameLayerAPI;
import vg.civcraft.mc.namelayer.permission.PermissionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HappyGhastManager {

    private static final NamespacedKey GHAST_KEY = new NamespacedKey("repelshitters", "stats_modified");

    /** Tiers of effect a bastion can have on a Happy Ghast. CLAIMS is strictest. */
    public enum GhastBastionTier { NONE, CITY, CLAIMS }

    // --- State tracking ---
    /** UUIDs of ghasts currently suppressed by a city bastion */
    private final Set<UUID> suppressedGhasts = new HashSet<>();
    /** Original base flying-speed before city suppression was applied */
    private final Map<UUID, Double> originalSpeeds = new HashMap<>();
    /**
     * Last time (ms) each player received a ghast-state action bar message.
     * Used to rate-limit messages to ~once per 2 seconds.
     */
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    /** Tick counter for staggering particle spawns inside the suppression task */
    private int particleTick = 0;

    /**
     * UUIDs of ghasts currently inside hostile claims airspace.
     * Updated every 5 ticks by the classification task; consumed every tick
     * by the claims enforcement task.
     */
    private final Set<UUID> inClaimsGhasts = new HashSet<>();
    /**
     * Last recorded position for each ghast that was OUTSIDE hostile claims.
     * The enforcement task teleports in-claims ghasts back to this position.
     */
    private final Map<UUID, Location> lastSafeLocations = new HashMap<>();

    private final RepelShitters plugin;
    private BukkitTask suppressionTask;
    private BukkitTask claimsEnforcementTask;

    public HappyGhastManager(RepelShitters plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void startSuppressionTask() {
        suppressionTask = new BukkitRunnable() {
            @Override
            public void run() {
                particleTick++;
                // Deduplicate: if multiple players ride the same ghast, process it only once per tick.
                Set<UUID> processedThisTick = new HashSet<>();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!(player.getVehicle() instanceof HappyGhast ghast)) continue;
                    if (!processedThisTick.add(ghast.getUniqueId())) continue;

                    GhastBastionTier tier = classifyHostileTier(ghast);

                    switch (tier) {
                        case CITY -> {
                            // Record safe location while in city (not claims)
                            lastSafeLocations.put(ghast.getUniqueId(), ghast.getLocation().clone());
                            inClaimsGhasts.remove(ghast.getUniqueId());
                            handleCityTier(ghast, player);
                        }
                        case NONE -> {
                            // Record safe location and clean up claims state
                            lastSafeLocations.put(ghast.getUniqueId(), ghast.getLocation().clone());
                            inClaimsGhasts.remove(ghast.getUniqueId());
                            if (suppressedGhasts.contains(ghast.getUniqueId())) {
                                removeSuppression(ghast);
                            }
                        }
                        case CLAIMS -> {
                            // Register in the enforcement set — the 1-tick task will teleport it back.
                            inClaimsGhasts.add(ghast.getUniqueId());
                            if (!suppressedGhasts.contains(ghast.getUniqueId())) {
                                applySpeedSuppression(ghast);
                            }
                            // Ring of sparks around the ghast's body outline every 10 ticks (~500ms)
                            if (particleTick % 2 == 0) {
                                Location ringCenter = ghast.getLocation().add(0, 1.5, 0);
                                double r = 1.5;
                                int pts = 12; // every 30 degrees
                                for (int i = 0; i < pts; i++) {
                                    double angle = (2 * Math.PI * i) / pts;
                                    Location pt = ringCenter.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                                    pt.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, pt, 2, 0.1, 0.3, 0.1, 0.03);
                                }
                            }
                            // Rate-limited "blocked" message
                            List<Player> claimsPassengers = getAllPassengerPlayers(ghast);
                            if (claimsPassengers.isEmpty()) claimsPassengers = List.of(player);
                            sendRateLimitedMessage(claimsPassengers,
                                    "§c⚡ §lBlocked! §rYour ghast cannot enter enemy claims airspace!");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);

        startClaimsEnforcementTask();
    }

    private void startClaimsEnforcementTask() {
        claimsEnforcementTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID ghastId : new HashSet<>(inClaimsGhasts)) {
                    Entity e = Bukkit.getEntity(ghastId);
                    if (!(e instanceof HappyGhast ghast) || !ghast.isValid()) {
                        inClaimsGhasts.remove(ghastId);
                        lastSafeLocations.remove(ghastId);
                        continue;
                    }

                    // Cheap exit-check: if no bastions at current position, the ghast
                    // has left the field — remove from set so enforcement stops immediately.
                    if (Bastion.getBastionManager().getBlockingBastions(ghast.getLocation()).isEmpty()) {
                        inClaimsGhasts.remove(ghastId);
                        continue;
                    }

                    Location safe = lastSafeLocations.get(ghastId);
                    if (safe != null) {
                        // Capture current position before teleporting (needed for bounce direction)
                        Location currentPos = ghast.getLocation().clone();
                        // Forceful teleport back to last known safe position —
                        // this is the only API that reliably overrides vehicle packet movement.
                        ghast.teleport(safe);
                        // Apply small repel push away from the boundary so it feels like hitting a wall
                        Vector repelDir = safe.toVector().subtract(currentPos.toVector());
                        if (repelDir.lengthSquared() > 0.0001) {
                            ghast.setVelocity(repelDir.normalize()
                                    .multiply(plugin.getConfigManager().getRepelStrength()));
                        }
                    } else {
                        // No safe location recorded yet (e.g., ghast spawned inside claims) — freeze it
                        ghast.setVelocity(new Vector(0, 0, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void stopSuppressionTask() {
        if (suppressionTask != null) {
            suppressionTask.cancel();
            suppressionTask = null;
        }
        if (claimsEnforcementTask != null) {
            claimsEnforcementTask.cancel();
            claimsEnforcementTask = null;
        }
        inClaimsGhasts.clear();
        lastSafeLocations.clear();
        // Restore all suppressed ghasts on shutdown
        for (UUID id : new HashSet<>(suppressedGhasts)) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof HappyGhast ghast) {
                removeSuppression(ghast);
            } else {
                // Entity gone; just clean up maps
                suppressedGhasts.remove(id);
                originalSpeeds.remove(id);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Stat modification (kept from original — speed/health on spawn/mount)
    // -----------------------------------------------------------------------

    public void modifyGhastStats(LivingEntity entity) {
        if (entity.getType() != EntityType.HAPPY_GHAST) return;

        var pdc = entity.getPersistentDataContainer();
        int ghastHash = pdc.has(GHAST_KEY) ? pdc.get(GHAST_KEY, PersistentDataType.INTEGER) : -1;

        if (ghastHash == plugin.getConfigManager().getGhastConfigHash()) {
            return;
        }

        var speedAttribute = entity.getAttribute(Attribute.FLYING_SPEED);
        if (speedAttribute != null) {
            // Only set base speed if the ghast is not currently suppressed
            if (!suppressedGhasts.contains(entity.getUniqueId())) {
                speedAttribute.setBaseValue(plugin.getConfigManager().getGhastBlocksPerSecond());
            }
        }

        var maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(plugin.getConfigManager().getGhastMaxHealth());
            entity.setHealth(maxHealthAttribute.getBaseValue());
        }

        pdc.set(GHAST_KEY, PersistentDataType.INTEGER, plugin.getConfigManager().getGhastConfigHash());
    }

    // -----------------------------------------------------------------------
    // EntityMoveEvent handler — Claims bastion hard repulsion
    // -----------------------------------------------------------------------

    public void handleGhastMoveForClaims(EntityMoveEvent event) {
        if (event.getEntity().getType() != EntityType.HAPPY_GHAST) return;
        HappyGhast ghast = (HappyGhast) event.getEntity();

        Location from = event.getFrom();
        // Snapshot 'to' before we potentially overwrite it — needed for particles/bounce direction
        Location to = event.getTo().clone();

        // Check destination position
        GhastBastionTier toTier = classifyHostileTierAt(ghast, to);
        if (toTier != GhastBastionTier.CLAIMS) return;

        // Override destination back to source — more reliable than setCancelled for
        // player-controlled vehicles where the movement packet is handled differently.
        event.setTo(from.clone());

        boolean alreadyInside = classifyHostileTierAt(ghast, from) == GhastBastionTier.CLAIMS;
        List<Player> passengers = getAllPassengerPlayers(ghast);

        if (alreadyInside) {
            // Ghast is trapped inside enemy claims; keep it locked, rate-limit message
            sendRateLimitedMessage(passengers,
                    "§c⚡ §lLocked! §rYour ghast is trapped inside enemy claims airspace.");
        } else {
            // Ghast hit the boundary from outside — bounce it back
            // Ring of sparks around the ghast's mid-body outline so riders can see the impact clearly.
            // 'from' is the ghast center (safe side); ring radius ~1.8 to outline a Happy Ghast body.
            Location impactCenter = from.clone().add(0, 1.5, 0);
            int ringPoints = 18; // every 20 degrees
            double ringRadius = 1.8;
            for (int i = 0; i < ringPoints; i++) {
                double angle = (2 * Math.PI * i) / ringPoints;
                Location ringPt = impactCenter.clone().add(
                        Math.cos(angle) * ringRadius, 0, Math.sin(angle) * ringRadius);
                ringPt.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, ringPt, 4, 0.15, 0.5, 0.15, 0.06);
            }
            // Central burst for extra "hit the wall" pop
            from.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, impactCenter, 25, 1.5, 1.0, 1.5, 0.08);

            // Reverse-velocity bounce scheduled 1 tick later to avoid being overridden
            double repel = plugin.getConfigManager().getRepelStrength();
            Vector dir = from.toVector().subtract(to.toVector());
            // Guard against zero-length vector (shouldn't happen but be safe)
            if (dir.lengthSquared() > 0) {
                dir.normalize().multiply(repel).setY(Math.max(dir.getY(), 0.05));
            } else {
                dir = new Vector(0, 0.1, 0);
            }
            final Vector bounce = dir;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (ghast.isValid()) {
                        ghast.setVelocity(bounce);
                    }
                }
            }.runTask(plugin);

            sendRateLimitedMessage(passengers,
                    "§c⚡ §lBlocked! §rYour ghast hits a reinforced claims barrier!");
        }
    }

    // -----------------------------------------------------------------------
    // EntityMoveEvent handler — City bastion altitude hard-clamp
    // Called from GhastListener at NORMAL priority so it runs after claims check.
    // -----------------------------------------------------------------------

    public void handleGhastMoveForCity(EntityMoveEvent event) {
        if (event.getEntity().getType() != EntityType.HAPPY_GHAST) return;
        HappyGhast ghast = (HappyGhast) event.getEntity();

        GhastBastionTier toTier = classifyHostileTierAt(ghast, event.getTo());
        if (toTier != GhastBastionTier.CITY) return;

        // Hard-clamp altitude via setTo so the cap is enforced the instant
        // the ghast tries to cross it — no waiting for the next task tick.
        Location to = event.getTo().clone();
        int highestBlock = to.getWorld().getHighestBlockYAt(to);
        double maxY = highestBlock + plugin.getConfigManager().getCityAltitudeCap() + 1.0;
        if (to.getY() > maxY) {
            to.setY(maxY);
            event.setTo(to);
        }
    }

    // -----------------------------------------------------------------------
    // City tier handling (called from suppression task)
    // -----------------------------------------------------------------------

    private void handleCityTier(HappyGhast ghast, Player pilot) {
        // Apply speed suppression once when entering
        if (!suppressedGhasts.contains(ghast.getUniqueId())) {
            applySpeedSuppression(ghast);
        }

        // Enforce altitude cap every task tick (every 5 ticks)
        enforceAltitudeCap(ghast);

        // Spawn crying particles every 10 ticks (every other task tick)
        // Raised to +3.5 so riders sitting on top of the ghast can see them
        if (particleTick % 2 == 0) {
            Location loc = ghast.getLocation().add(0, 3.5, 0);
            loc.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 8, 0.6, 0.3, 0.6, 0.0);
        }

        // Send action bar to all passengers, rate-limited to ~2s
        List<Player> passengers = getAllPassengerPlayers(ghast);
        if (passengers.isEmpty()) passengers = List.of(pilot);
        sendRateLimitedMessage(passengers,
                "§6😢 §lUnhappy! §rYour ghast is suppressed in enemy city airspace.");
    }

    private void applySpeedSuppression(HappyGhast ghast) {
        var attr = ghast.getAttribute(Attribute.FLYING_SPEED);
        if (attr == null) return;
        // Store the configured base speed (not the current value, which may already be modified)
        double baseSpeed = plugin.getConfigManager().getGhastBlocksPerSecond();
        originalSpeeds.put(ghast.getUniqueId(), baseSpeed);
        attr.setBaseValue(baseSpeed * plugin.getConfigManager().getCitySpeedMultiplier());
        suppressedGhasts.add(ghast.getUniqueId());
    }

    private void removeSuppression(HappyGhast ghast) {
        var attr = ghast.getAttribute(Attribute.FLYING_SPEED);
        if (attr != null) {
            double restored = originalSpeeds.getOrDefault(
                    ghast.getUniqueId(),
                    plugin.getConfigManager().getGhastBlocksPerSecond());
            attr.setBaseValue(restored);
        }
        suppressedGhasts.remove(ghast.getUniqueId());
        originalSpeeds.remove(ghast.getUniqueId());
        // Clear per-player rate-limit entries for all passengers so they get a
        // "normal" message next time they enter a field
        for (Player p : getAllPassengerPlayers(ghast)) {
            lastMessageTime.remove(p.getUniqueId());
        }
    }

    private void enforceAltitudeCap(HappyGhast ghast) {
        Location loc = ghast.getLocation();
        int highestBlock = loc.getWorld().getHighestBlockYAt(loc);
        double maxY = highestBlock + plugin.getConfigManager().getCityAltitudeCap() + 1.0;

        if (loc.getY() > maxY) {
            Vector vel = ghast.getVelocity();
            // Push down firmly — -0.25 gives a clear, snappy descent
            ghast.setVelocity(new Vector(vel.getX(), -0.25, vel.getZ()));
        }
    }

    // -----------------------------------------------------------------------
    // Bastion classification helpers
    // -----------------------------------------------------------------------

    /**
     * Classifies the strictest hostile bastion tier at the ghast's current location.
     */
    public GhastBastionTier classifyHostileTier(HappyGhast ghast) {
        return classifyHostileTierAt(ghast, ghast.getLocation());
    }

    /**
     * Classifies the strictest hostile bastion tier at a specific location,
     * using the ghast's pilot for permission checks.
     */
    private GhastBastionTier classifyHostileTierAt(HappyGhast ghast, Location loc) {
        var bastions = Bastion.getBastionManager().getBlockingBastions(loc);
        if (bastions.isEmpty()) return GhastBastionTier.NONE;

        // Determine the pilot: the first direct passenger who is a Player
        Player pilot = null;
        for (Entity passenger : ghast.getPassengers()) {
            if (passenger instanceof Player p) {
                pilot = p;
                break;
            }
        }
        if (pilot == null) return GhastBastionTier.NONE;

        final Player finalPilot = pilot;
        GhastBastionTier result = GhastBastionTier.NONE;

        for (BastionBlock bastion : bastions) {
            // Skip friendly bastions
            if(NameLayerAPI.getGroupManager().hasAccess(bastion.getGroup(), finalPilot.getUniqueId(), PermissionType.getPermission(Permissions.BASTION_PLACE))){
                continue;
            }

            //if (bastion.canPlace(finalPilot)) continue;

            // Optionally skip immature bastions
            if (plugin.getConfigManager().isRequireMaturity()) {
                long age = System.currentTimeMillis() - bastion.getPlaced();
                if (age < bastion.getType().getWarmupTime()) continue;
            }

            String typeName = bastion.getType().getName();
            if (typeName.equals(plugin.getConfigManager().getClaimsBastionName())) {
                return GhastBastionTier.CLAIMS; // Strictest — short-circuit
            } else if (typeName.equals(plugin.getConfigManager().getCityBastionName())) {
                result = GhastBastionTier.CITY;
            }
            // Any other type (e.g., babbysfirstbastion) → no effect, leave result as-is
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    /**
     * Recursively collects all Player passengers from a vehicle and its passengers.
     */
    public List<Player> getAllPassengerPlayers(Entity entity) {
        List<Player> players = new ArrayList<>();
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player p) {
                players.add(p);
            }
            // Recurse for stacked passengers
            players.addAll(getAllPassengerPlayers(passenger));
        }
        return players;
    }

    /**
     * Sends an action bar message to each player, rate-limited to once per 2 seconds.
     */
    private void sendRateLimitedMessage(List<Player> players, String message) {
        long now = System.currentTimeMillis();
        for (Player p : players) {
            long last = lastMessageTime.getOrDefault(p.getUniqueId(), 0L);
            if (now - last >= 2000L) {
                p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize(message));
                lastMessageTime.put(p.getUniqueId(), now);
            }
        }
    }
}
