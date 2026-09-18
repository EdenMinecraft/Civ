package com.programmerdan.minecraft.simpleadminhacks.hacks.basic;

import com.destroystokyo.paper.MaterialTags;
import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.framework.BasicHack;
import com.programmerdan.minecraft.simpleadminhacks.framework.BasicHackConfig;
import com.programmerdan.minecraft.simpleadminhacks.framework.autoload.AutoLoad;
import com.programmerdan.minecraft.simpleadminhacks.framework.autoload.DataParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CopperRail extends BasicHack implements CommandExecutor {

    private static final double METRES_PER_SECOND_TO_SPEED = 0.05;
    private static final double FURNACE_MAX_SPEED_FACTOR = 0.5;
    private static final double GRINDER_MOVING_SPEED = 0.04;
    private static final int GRINDER_HUD_INTERVAL = 10;
    private static final double GRINDER_TURN_RATE = Math.toRadians(3);
    // Fuelled carts must always beat the unpowered putter speed by at least this much (m/s),
    // regardless of how grinderSpeedMetresPerSecond/grinderOffRailSpeed/grinderSurfaces are configured.
    private static final int GRINDER_POWERED_MARGIN = 2;

    @AutoLoad
    private boolean deoxidise;

    @AutoLoad
    private double damage;

    @AutoLoad(isRequired = false)
    private boolean grinder = true;

    @AutoLoad(isRequired = false)
    private boolean ridePoweredCarts = true;

    @AutoLoad(isRequired = false, processor = DataParser.MATERIAL)
    private List<Material> grinderFuel = new ArrayList<>(List.of(Material.AMETHYST_SHARD));

    @AutoLoad(isRequired = false)
    private int grinderFuelPerItem = 800;

    @AutoLoad(isRequired = false)
    private int grinderMaxFuel = 8000;

    @AutoLoad(isRequired = false)
    private int grinderSpeedMetresPerSecond = 12;

    @AutoLoad(isRequired = false)
    private int grinderSpeedHardCap = 28;

    @AutoLoad(isRequired = false)
    private int grinderOffRailSpeed = 8;

    @AutoLoad(isRequired = false)
    private double grinderStepHeight = 0.5;

    @AutoLoad(isRequired = false)
    private int grinderUnpoweredSpeed = 4;

    @AutoLoad(isRequired = false)
    private double grinderUnpoweredGrindChance = 0.05;

    @AutoLoad(isRequired = false, processor = DataParser.MATERIAL)
    private Material nitrousMaterial;

    @AutoLoad(isRequired = false)
    private String nitrousLore = "";

    @AutoLoad(isRequired = false)
    private int nitrousSpeed = 26;

    @AutoLoad(isRequired = false)
    private int nitrousDurationTicks = 60;

    @AutoLoad(isRequired = false)
    private int grinderNitrousHardCap = 29;

    private boolean formingBlock = false;

    private BukkitTask grinderTask;
    private final Set<UUID> grinderActive = new HashSet<>();
    private final Map<Material, Double> grinderSurfaceSpeeds = new HashMap<>();
    private final Map<UUID, Long> grinderBoostUntil = new HashMap<>();
    private final Map<UUID, Vector> grinderLastHeading = new HashMap<>();
    private String nitrousLoreKey = "";
    private long grinderTicks = 0L;
    private long lastGrindSound = 0L;

    public CopperRail(SimpleAdminHacks plugin, BasicHackConfig config) {
        super(plugin, config);
    }

    @Override
    public void onEnable() {
        super.onEnable();

        grinderSurfaceSpeeds.clear();
        ConfigurationSection surfaces = config().getBase().getConfigurationSection("grinderSurfaces");
        if (surfaces != null) {
            for (String key : surfaces.getKeys(false)) {
                Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                if (material == null) {
                    plugin().warning("CopperRail: unknown grinderSurfaces material '" + key + "'");
                    continue;
                }
                grinderSurfaceSpeeds.put(material, surfaces.getDouble(key));
            }
        }
        nitrousLoreKey = normalizeLore(nitrousLore);

        int minPowered = grinderUnpoweredSpeed + GRINDER_POWERED_MARGIN;
        if (grinderSpeedMetresPerSecond < minPowered) {
            plugin().warning("CopperRail: grinderSpeedMetresPerSecond (" + grinderSpeedMetresPerSecond
                + ") is too close to grinderUnpoweredSpeed (" + grinderUnpoweredSpeed
                + "); it will be raised to " + minPowered + " at runtime so amethyst is always faster on rail.");
        }
        if (grinderOffRailSpeed < minPowered) {
            plugin().warning("CopperRail: grinderOffRailSpeed (" + grinderOffRailSpeed
                + ") is too close to grinderUnpoweredSpeed (" + grinderUnpoweredSpeed
                + "); it will be raised to " + minPowered + " at runtime so amethyst is always faster off rail.");
        }

        if (grinder) {
            this.grinderTask = Bukkit.getScheduler().runTaskTimer(plugin(), this::tickGrinderCarts, 1L, 1L);
        }

        plugin().registerCommand("nitrous", this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (this.grinderTask != null) {
            this.grinderTask.cancel();
            this.grinderTask = null;
        }
        this.grinderActive.clear();
        this.grinderBoostUntil.clear();
        this.grinderLastHeading.clear();
    }

    private static String normalizeLore(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Component parsed = LegacyComponentSerializer.legacyAmpersand().deserialize(raw.replace('§', '&'));
        return PlainTextComponentSerializer.plainText().serialize(parsed).toLowerCase(Locale.ROOT).trim();
    }

    private boolean isNitrous(ItemStack item) {
        if (item == null || nitrousMaterial == null || nitrousLoreKey.isEmpty() || item.getType() != nitrousMaterial) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        List<Component> lore = meta.lore();
        if (lore == null) {
            return false;
        }
        for (Component line : lore) {
            if (PlainTextComponentSerializer.plainText().serialize(line).toLowerCase(Locale.ROOT).trim().equals(nitrousLoreKey)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createNitrousItem(int amount) {
        ItemStack item = new ItemStack(nitrousMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        Component loreLine = LegacyComponentSerializer.legacyAmpersand().deserialize(nitrousLore.replace('§', '&'))
            .decoration(TextDecoration.ITALIC, false);
        meta.lore(List.of(loreLine));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isBoosting(UUID id, long tick) {
        Long until = grinderBoostUntil.get(id);
        if (until == null) {
            return false;
        }
        if (tick >= until) {
            grinderBoostUntil.remove(id);
            return false;
        }
        return true;
    }

    private void triggerNitrous(PoweredMinecart cart, Player player) {
        if (cart.getFuel() <= 0) {
            player.sendActionBar(Component.text("Grinder cart is out of amethyst.", NamedTextColor.RED));
            return;
        }
        grinderBoostUntil.put(cart.getUniqueId(), grinderTicks + nitrousDurationTicks);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        }
        cart.getWorld().playSound(cart.getLocation(), Sound.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.8f, 1.4f);
        player.sendActionBar(Component.text("Nitrous!", NamedTextColor.AQUA));
    }

    private boolean isFuel(ItemStack item) {
        return item != null && !grinderFuel.isEmpty() && grinderFuel.contains(item.getType());
    }

    private void refuelGrinder(PoweredMinecart cart, Player player) {
        int current = cart.getFuel();
        if (current >= grinderMaxFuel) {
            player.sendActionBar(Component.text("Grinder cart fuel is full.", NamedTextColor.GRAY));
            return;
        }
        cart.setFuel(Math.min(grinderMaxFuel, current + grinderFuelPerItem));
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        }
        cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.7f, 1.0f);
        player.sendActionBar(grinderFuelBar(cart.getFuel()));
    }

    private Component grinderFuelBar(int fuel) {
        int percent = Math.max(0, Math.min(100, (int) Math.round(100.0 * fuel / Math.max(1, grinderMaxFuel))));
        int filled = (int) Math.round(percent / 10.0);
        NamedTextColor color = percent <= 15 ? NamedTextColor.RED
            : percent <= 40 ? NamedTextColor.GOLD
            : NamedTextColor.GREEN;
        String bar = "▊".repeat(filled) + "░".repeat(10 - filled);
        return Component.text("Fuel ", NamedTextColor.GRAY)
            .append(Component.text(bar + " " + percent + "%", color));
    }

    private void tickGrinderCarts() {
        long tick = this.grinderTicks++;
        boolean showHud = tick % GRINDER_HUD_INTERVAL == 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof PoweredMinecart cart)) {
                continue;
            }

            UUID id = cart.getUniqueId();
            boolean powered = cart.getFuel() > 0;

            if (!powered) {
                this.grinderBoostUntil.remove(id);
                if (this.grinderActive.remove(id)) {
                    player.sendActionBar(Component.text("Grinder cart is out of amethyst.", NamedTextColor.RED));
                } else if (showHud) {
                    player.sendActionBar(grinderFuelBar(0));
                }
                if (grinderUnpoweredSpeed <= 0) {
                    continue;
                }
            } else if (showHud) {
                player.sendActionBar(grinderFuelBar(cart.getFuel()));
            }

            boolean onRail = isOnRail(cart);
            int minPoweredSpeed = grinderUnpoweredSpeed + GRINDER_POWERED_MARGIN;
            double mps;
            if (!powered) {
                mps = Math.min(grinderUnpoweredSpeed, grinderSpeedHardCap);
            } else if (onRail) {
                mps = Math.min(Math.max(grinderSpeedMetresPerSecond, minPoweredSpeed), grinderSpeedHardCap);
            } else if (isBoosting(id, tick)) {
                mps = Math.min(nitrousSpeed, grinderNitrousHardCap);
            } else {
                double surface = grinderSurfaceSpeeds.getOrDefault(surfaceUnder(cart), (double) grinderOffRailSpeed);
                mps = Math.min(Math.max(surface, minPoweredSpeed), grinderSpeedHardCap);
            }

            double target = mps * METRES_PER_SECOND_TO_SPEED;
            cart.setMaxSpeed(target / FURNACE_MAX_SPEED_FACTOR);

            Vector velocity = cart.getVelocity();
            boolean isMoving = Math.hypot(velocity.getX(), velocity.getZ()) > GRINDER_MOVING_SPEED;
            boolean isForwardHeld = player.getCurrentInput().isForward();
            Vector heading;
            if (isMoving) {
                heading = new Vector(velocity.getX(), 0.0, velocity.getZ()).normalize();
                if (!onRail) {
                    heading = steerToward(heading, player.getLocation().getYaw());
                }
                this.grinderLastHeading.put(id, heading);
            } else if (isForwardHeld) {
                heading = yawToDirection(player.getLocation().getYaw());
            } else {
                // Stalled with no input. Fall back to the last direction the cart was actually
                // travelling so a wall hit doesn't cut step-up off after a tick or two - the
                // collision that zeroes horizontal velocity happens well before the cart has had
                // time to climb, and this design has no gas pedal to hold to keep retrying.
                heading = this.grinderLastHeading.get(id);
                if (heading == null) {
                    continue;
                }
            }

            double hopVelocity = onRail ? Double.NaN : tryStepUp(cart, heading);
            if (!isMoving && !isForwardHeld && Double.isNaN(hopVelocity)) {
                continue; // genuinely stopped with nothing to climb; don't force perpetual motion
            }
            double verticalVelocity = Double.isNaN(hopVelocity) ? velocity.getY() : Math.max(velocity.getY(), hopVelocity);

            if (powered) {
                this.grinderActive.add(id);
            }
            cart.setVelocity(new Vector(heading.getX() * target, verticalVelocity, heading.getZ() * target));
        }
    }

    private static Material surfaceUnder(Minecart cart) {
        Block in = cart.getLocation().getBlock();
        return in.isPassable() ? in.getRelative(BlockFace.DOWN).getType() : in.getType();
    }

    /**
     * Detects a climbable ledge directly ahead and, if found, returns an upward velocity assist
     * to let normal vehicle physics carry the cart up and over it. Returns {@code Double.NaN} when
     * there's nothing to climb. Teleporting a ridden vehicle is unreliable on Paper 1.21.8 even with
     * TeleportFlag.EntityState.RETAIN_PASSENGERS, so this nudges velocity instead - the same
     * mechanism already used for all other off-rail movement.
     */
    private double tryStepUp(Minecart cart, Vector heading) {
        if (grinderStepHeight <= 0.0) {
            return Double.NaN;
        }
        BoundingBox forward = cart.getBoundingBox().clone().shift(heading.getX() * 0.15, 0.0, heading.getZ() * 0.15);
        if (isClear(cart.getWorld(), forward)) {
            return Double.NaN;
        }
        for (double lift = 0.05; lift <= grinderStepHeight + 0.001; lift += 0.05) {
            BoundingBox lifted = forward.clone().shift(0.0, lift, 0.0);
            if (isClear(cart.getWorld(), lifted)) {
                return Math.sqrt(0.08 * lift);
            }
        }
        return Double.NaN;
    }

    /**
     * Whether the given world-space box is free of any solid block collision. Used to find the
     * smallest lift that lets the cart's actual body fit over an obstacle, rather than reasoning
     * about which row/block is "ahead" - that approach drifted out of sync once the cart was
     * resting on a partial-height block (a slab or stair mid-climb) instead of a full block.
     */
    private static boolean isClear(World world, BoundingBox box) {
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.isPassable()) {
                        continue;
                    }
                    BoundingBox local = box.clone().shift(-x, -y, -z);
                    if (block.getCollisionShape().overlaps(local)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Vector yawToDirection(float yaw) {
        float normalised = (yaw % 360.0f + 360.0f) % 360.0f;
        if (normalised < 45.0f || normalised >= 315.0f) {
            return new Vector(0.0, 0.0, 1.0);
        }
        if (normalised < 135.0f) {
            return new Vector(-1.0, 0.0, 0.0);
        }
        if (normalised < 225.0f) {
            return new Vector(0.0, 0.0, -1.0);
        }
        return new Vector(1.0, 0.0, 0.0);
    }

    private static boolean isOnRail(Minecart cart) {
        Block block = cart.getLocation().getBlock();
        return MaterialTags.RAILS.isTagged(block) || MaterialTags.RAILS.isTagged(block.getRelative(BlockFace.DOWN));
    }

    private static Vector steerToward(Vector heading, float yaw) {
        double current = Math.atan2(heading.getX(), heading.getZ());
        double desired = -Math.toRadians(yaw);
        double delta = Math.atan2(Math.sin(desired - current), Math.cos(desired - current));
        double angle = current + Math.max(-GRINDER_TURN_RATE, Math.min(GRINDER_TURN_RATE, delta));
        return new Vector(Math.sin(angle), 0.0, Math.cos(angle));
    }

    private static boolean weatheringTarget(Block block, boolean grinding) {
        BlockState nms = ((CraftBlock) block).getNMS();
        return grinding
            ? WeatheringCopper.getPrevious(nms).isPresent()
            : WeatheringCopper.getNext(nms.getBlock()).isPresent();
    }

    @EventHandler
    public void on(VehicleExitEvent event) {
        UUID id = event.getVehicle().getUniqueId();
        this.grinderActive.remove(id);
        this.grinderBoostUntil.remove(id);
        this.grinderLastHeading.remove(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void on(PlayerInteractEntityEvent event) {
        if (!grinder && !ridePoweredCarts) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof PoweredMinecart cart)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (grinder && isFuel(held)) {
            refuelGrinder(cart, player);
            event.setCancelled(true);
            return;
        }

        if (grinder && (held.getType() == Material.COAL || held.getType() == Material.CHARCOAL)) {
            player.sendActionBar(Component.text("Only amethyst fuels a grinder cart.", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        if (!ridePoweredCarts || !cart.getPassengers().isEmpty()) {
            return;
        }

        boolean mounted = cart.addPassenger(player);
        if (!mounted || player.getVehicle() == null) {
            ((CraftPlayer) player).getHandle().startRiding(((CraftEntity) cart).getHandle(), true);
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void on(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) {
            return;
        }

        boolean hasPlayer = false;
        for (Entity entity : minecart.getPassengers()) {
            if (entity instanceof Player) {
                hasPlayer = true;
                break;
            }
        }

        if (!hasPlayer) {
            return;
        }

        boolean poweredCart = grinder && minecart instanceof PoweredMinecart;
        boolean onRail = poweredCart && isOnRail(minecart);
        int cartFuel = poweredCart ? ((PoweredMinecart) minecart).getFuel() : 0;
        boolean grinding = poweredCart && cartFuel > 0 && onRail;
        boolean grindingUnpowered = poweredCart && cartFuel <= 0 && onRail
            && grinderUnpoweredSpeed > 0 && grinderUnpoweredGrindChance > 0;

        if (!grinding && !grindingUnpowered) {
            if (this.damage <= 0 || poweredCart) {
                return;
            }
        }

        Location to = event.getTo();
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }

        int signX = from.getBlockX() > to.getBlockX() ? 1 : -1;
        int signZ = from.getBlockZ() > to.getBlockZ() ? 1 : -1;
        boolean firstBlock = true;

        List<Block> copperBlocks = new ArrayList<>(4);
        for (int x = to.getBlockX(); x != to.getBlockX() + (from.getBlockX() - to.getBlockX()) + signX; x += signX) {
            for (int z = to.getBlockZ(); z != to.getBlockZ() + (from.getBlockZ() - to.getBlockZ()) + signZ; z += signZ) {
                if (firstBlock) {
                    firstBlock = false;
                    continue;
                }
                Location location = new Location(minecart.getWorld(), x, from.getY(), z);
                Block topCopperBlock = location.getBlock().getRelative(BlockFace.DOWN);
                Block belowCopperBlock = topCopperBlock.getRelative(BlockFace.DOWN);
                if (weatheringTarget(topCopperBlock, grinding || grindingUnpowered)) {
                    copperBlocks.add(topCopperBlock);
                }
                if (weatheringTarget(belowCopperBlock, grinding || grindingUnpowered)) {
                    copperBlocks.add(belowCopperBlock);
                }
            }
        }

        if (grinding || grindingUnpowered) {
            if (grindingUnpowered && ThreadLocalRandom.current().nextFloat() >= grinderUnpoweredGrindChance) {
                return;
            }
            grind(minecart, from, to, copperBlocks);
            return;
        }

        for (Block copperBlock : copperBlocks) {
            Material currentMaterial = copperBlock.getType();
            Material nextMaterial = getNextStage(currentMaterial);

            if (nextMaterial == null) {
                continue;
            }

            CopperStage stage = CopperStage.from(currentMaterial);
            float chanceModifier = (stage != null) ? stage.chance : 0.0f;

            if (this.damage * chanceModifier > ThreadLocalRandom.current().nextFloat()) {
                org.bukkit.block.BlockState newState = copperBlock.getState();
                newState.setType(nextMaterial);
                BlockFormEvent formEvent = new BlockFormEvent(copperBlock, newState);

                try {
                    formingBlock = true;
                    Bukkit.getPluginManager().callEvent(formEvent);
                    if (!formEvent.isCancelled()) {
                        newState.update(true);
                    }
                } finally {
                    formingBlock = false;
                }
            }
        }
    }

    private void grind(Minecart minecart, Location from, Location to, List<Block> copperBlocks) {
        boolean cleaned = false;
        for (Block copperBlock : copperBlocks) {
            Optional<BlockState> previous = WeatheringCopper.getPrevious(((CraftBlock) copperBlock).getNMS());
            while (previous.isPresent()) {
                copperBlock.setType(previous.get().getBukkitMaterial());
                cleaned = true;
                previous = WeatheringCopper.getPrevious(((CraftBlock) copperBlock).getNMS());
            }
        }

        Location effect = from.clone().add((to.getX() - from.getX()) / 2.0, 0.0, (to.getZ() - from.getZ()) / 2.0);
        minecart.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, effect, 12, 0.25, 0.1, 0.25, 0.0);
        if (cleaned) {
            long now = System.currentTimeMillis();
            if (now - this.lastGrindSound > 400L) {
                this.lastGrindSound = now;
                minecart.getWorld().playSound(effect, Sound.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.35f, 1.3f);
                minecart.getWorld().playEffect(effect, Effect.OXIDISED_COPPER_SCRAPE, 0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void on(PlayerInteractEvent event) {
        if (grinder && event.getHand() == EquipmentSlot.HAND && event.getAction().isRightClick()
            && event.getPlayer().getVehicle() instanceof PoweredMinecart cart) {
            ItemStack held = event.getItem();
            if (isFuel(held)) {
                refuelGrinder(cart, event.getPlayer());
                event.setCancelled(true);
                return;
            }
            if (isNitrous(held)) {
                triggerNitrous(cart, event.getPlayer());
                event.setCancelled(true);
                return;
            }
        }

        if (!this.deoxidise) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !MaterialTags.AXES.isTagged(item)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !MaterialTags.RAILS.isTagged(block)) {
            return;
        }

        boolean damaged = false;
        Player player = event.getPlayer();

        // First copper block directly underneath the rail
        Block topCopperBlock = block.getRelative(BlockFace.DOWN);
        Material previousTop = getPreviousStage(topCopperBlock.getType());

        while (previousTop != null && item.getType() != Material.AIR) {
            topCopperBlock.setType(previousTop);
            damaged = true;
            item.damage(1, player);
            previousTop = getPreviousStage(topCopperBlock.getType());
        }

        // Second copper block two spaces underneath the rail
        Block belowCopperBlock = topCopperBlock.getRelative(BlockFace.DOWN);
        Material previousBelow = getPreviousStage(belowCopperBlock.getType());

        while (previousBelow != null && item.getType() != Material.AIR) {
            belowCopperBlock.setType(previousBelow);
            damaged = true;
            item.damage(1, player);
            previousBelow = getPreviousStage(belowCopperBlock.getType());
        }

        if (!damaged) {
            return;
        }

        block.getWorld().playSound(block.getLocation(), Sound.ITEM_AXE_SCRAPE, SoundCategory.BLOCKS, 1, 1);
        block.getWorld().playEffect(block.getLocation(), Effect.OXIDISED_COPPER_SCRAPE, 0);

        event.setCancelled(true);
    }

    // It's not really fair for copper blocks that are below rails to naturally oxidise,
    // as it is easy to cheese by placing a waxed copper block every 9 blocks
    @EventHandler
    public void on(BlockFormEvent event) {
        if (formingBlock) {
            return;
        }

        Block block = event.getBlock();
        if (getNextStage(block.getType()) == null) {
            return;
        }

        Block railAbove = block.getRelative(BlockFace.UP);
        if (!MaterialTags.RAILS.isTagged(railAbove)) {
            railAbove = railAbove.getRelative(BlockFace.UP);
        }

        if (!MaterialTags.RAILS.isTagged(railAbove)) {
            return;
        }

        event.setCancelled(true);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("simpleadmin.nitrous")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (nitrousMaterial == null || nitrousLoreKey.isEmpty()) {
            sender.sendMessage(Component.text("Nitrous is not configured (set nitrousMaterial and nitrousLore).", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            return false;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            return false;
        }
        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be positive.", NamedTextColor.RED));
            return true;
        }

        int maxStack = nitrousMaterial.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            remaining -= stackAmount;
            player.getInventory().addItem(createNitrousItem(stackAmount)).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }

        player.sendMessage(Component.text("Gave you " + amount + " nitrous charge(s).", NamedTextColor.AQUA));
        return true;
    }
}