package com.programmerdan.minecraft.simpleadminhacks.hacks.basic;

import com.destroystokyo.paper.MaterialTags;
import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.framework.BasicHack;
import com.programmerdan.minecraft.simpleadminhacks.framework.BasicHackConfig;
import com.programmerdan.minecraft.simpleadminhacks.framework.autoload.AutoLoad;
import com.programmerdan.minecraft.simpleadminhacks.framework.autoload.DataParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CopperRail extends BasicHack {

    private static final double METRES_PER_SECOND_TO_SPEED = 0.05;
    private static final double FURNACE_MAX_SPEED_FACTOR = 0.5;
    private static final double GRINDER_MOVING_SPEED = 0.04;
    private static final int GRINDER_HUD_INTERVAL = 10;

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
    private int grinderSpeedHardCap = 20;

    private boolean formingBlock = false;

    private BukkitTask grinderTask;
    private final Set<UUID> grinderActive = new HashSet<>();
    private long grinderTicks = 0L;
    private long lastGrindSound = 0L;

    public CopperRail(SimpleAdminHacks plugin, BasicHackConfig config) {
        super(plugin, config);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (grinder) {
            this.grinderTask = Bukkit.getScheduler().runTaskTimer(plugin(), this::tickGrinderCarts, 1L, 1L);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (this.grinderTask != null) {
            this.grinderTask.cancel();
            this.grinderTask = null;
        }
        this.grinderActive.clear();
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
        return Component.text("Grinder fuel ", NamedTextColor.GRAY)
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
            int fuel = cart.getFuel();

            if (fuel <= 0) {
                if (this.grinderActive.remove(id)) {
                    player.sendActionBar(Component.text("Grinder cart is out of amethyst.", NamedTextColor.RED));
                } else if (showHud) {
                    player.sendActionBar(grinderFuelBar(0));
                }
                continue;
            }

            if (showHud) {
                player.sendActionBar(grinderFuelBar(fuel));
            }

            double target = Math.min(grinderSpeedMetresPerSecond, grinderSpeedHardCap) * METRES_PER_SECOND_TO_SPEED;
            cart.setMaxSpeed(target / FURNACE_MAX_SPEED_FACTOR);

            Vector velocity = cart.getVelocity();
            Vector heading;
            if (Math.hypot(velocity.getX(), velocity.getZ()) > GRINDER_MOVING_SPEED) {
                heading = new Vector(velocity.getX(), 0.0, velocity.getZ()).normalize();
            } else if (player.getCurrentInput().isForward()) {
                heading = yawToDirection(player.getLocation().getYaw());
            } else {
                continue;
            }

            this.grinderActive.add(id);
            cart.setVelocity(new Vector(heading.getX() * target, velocity.getY(), heading.getZ() * target));
        }
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

    private static boolean weatheringTarget(Block block, boolean grinding) {
        BlockState nms = ((CraftBlock) block).getNMS();
        return grinding
            ? WeatheringCopper.getPrevious(nms).isPresent()
            : WeatheringCopper.getNext(nms.getBlock()).isPresent();
    }

    @EventHandler
    public void on(VehicleExitEvent event) {
        this.grinderActive.remove(event.getVehicle().getUniqueId());
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
        boolean grinding = poweredCart && ((PoweredMinecart) minecart).getFuel() > 0;

        if (!grinding) {
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
                if (weatheringTarget(topCopperBlock, grinding)) {
                    copperBlocks.add(topCopperBlock);
                }
                if (weatheringTarget(belowCopperBlock, grinding)) {
                    copperBlocks.add(belowCopperBlock);
                }
            }
        }

        if (grinding) {
            grind(minecart, from, to, copperBlocks);
            return;
        }

        for (Block copperBlock : copperBlocks) {
            CraftBlock craftBlock = (CraftBlock) copperBlock;
            BlockState state = craftBlock.getNMS();
            ServerLevel level = ((CraftWorld) copperBlock.getWorld()).getHandle();
            // We damage the copper directly instead of using random ticking, as random ticking is easy to cheese
            // by placing waxed copper next to the rail, entirely preventing the rest of the rail from oxidising.
            WeatheringCopper copper = (WeatheringCopper) state.getBlock();
            float chanceModifier = copper.getChanceModifier();
            if (this.damage * chanceModifier > ThreadLocalRandom.current().nextFloat()) {
                copper.getNext(state).ifPresent((iblockdata2) -> {
                    try {
                        formingBlock = true;
                        CraftEventFactory.handleBlockFormEvent(level, craftBlock.getPosition(), iblockdata2, 3);
                    } finally {
                        formingBlock = false;
                    }
                });
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
            && event.getPlayer().getVehicle() instanceof PoweredMinecart cart && isFuel(event.getItem())) {
            refuelGrinder(cart, event.getPlayer());
            event.setCancelled(true);
            return;
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

        Block copperBlock = block.getRelative(BlockFace.DOWN);
        Optional<BlockState> previous = WeatheringCopper.getPrevious(((CraftBlock) copperBlock).getNMS());

        boolean damaged = false;
        CraftPlayer player = (CraftPlayer) event.getPlayer();

        while (previous.isPresent() && event.getItem().getType() != Material.AIR) {
            copperBlock.setType(previous.get().getBukkitMaterial());
            damaged = true;

            item.damage(1, player);
            previous = WeatheringCopper.getPrevious(((CraftBlock) copperBlock).getNMS());
        }

        copperBlock = copperBlock.getRelative(BlockFace.DOWN);
        previous = WeatheringCopper.getPrevious(((CraftBlock) copperBlock).getNMS());

        while (previous.isPresent() && event.getItem().getType() != Material.AIR) {
            copperBlock.setType(previous.get().getBukkitMaterial());
            damaged = true;

            item.damage(1, player);
            previous = WeatheringCopper.getPrevious(((CraftBlock) copperBlock).getNMS());
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

        Optional<net.minecraft.world.level.block.Block> next = WeatheringCopper.getNext(((CraftBlock) block).getNMS().getBlock());
        if (next.isEmpty()) {
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
}
