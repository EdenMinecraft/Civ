package vg.civcraft.mc.citadel.listener;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CopperGolemStatue;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vg.civcraft.mc.citadel.Citadel;
import vg.civcraft.mc.citadel.CitadelPermissionHandler;
import vg.civcraft.mc.citadel.CitadelUtility;
import vg.civcraft.mc.citadel.ReinforcementLogic;
import vg.civcraft.mc.citadel.model.Reinforcement;
import vg.civcraft.mc.civmodcore.ui.CivUI;

/**
 * Copper golems built from blocks reinforced on a group remember that group and the player who built
 * them, and may only use containers that are unreinforced, insecure, or reinforced on that same group
 * while their builder still has chest access on it. Golems without a group may only use unreinforced
 * or insecure containers.
 */
public class CopperGolemListener implements Listener {

    private static final Set<Material> COPPER_BLOCKS = EnumSet.of(
        Material.COPPER_BLOCK, Material.EXPOSED_COPPER, Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER,
        Material.WAXED_COPPER_BLOCK, Material.WAXED_EXPOSED_COPPER, Material.WAXED_WEATHERED_COPPER,
        Material.WAXED_OXIDIZED_COPPER);

    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.EAST, BlockFace.WEST};

    private final Citadel plugin;
    private final NamespacedKey groupKey;
    private final NamespacedKey ownerKey;

    // the golem spawns from the pumpkin's onPlace, which runs right after its BlockPlaceEvent in the same tick
    private Block lastPumpkin;
    private UUID lastPumpkinPlacer;
    private int lastPumpkinTick;

    public CopperGolemListener(Citadel plugin) {
        this.plugin = plugin;
        this.groupKey = new NamespacedKey(plugin, "golem_group");
        this.ownerKey = new NamespacedKey(plugin, "golem_owner");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberPumpkinPlacer(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();
        if (type != Material.CARVED_PUMPKIN && type != Material.JACK_O_LANTERN) {
            return;
        }
        lastPumpkin = event.getBlock();
        lastPumpkinPlacer = event.getPlayer().getUniqueId();
        lastPumpkinTick = Bukkit.getCurrentTick();
    }

    // a golem built from reinforced blocks needs every block of the pattern reinforced on the same group, and
    // must be built by a player with chest access on that group, since the golem gets that same access
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void preventUnauthorizedBuild(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_COPPERGOLEM) {
            return;
        }
        Block head = event.getLocation().getBlock();
        if (!plugin.getConfigManager().copperGolemsEnabled()) {
            event.setCancelled(true);
            UUID placer = getPumpkinPlacer(head);
            Player player = placer == null ? null : Bukkit.getPlayer(placer);
            if (player != null) {
                CivUI.error(player, "Copper golems are disabled");
            }
            return;
        }
        boolean anyReinforced = false;
        boolean allReinforced = true;
        Reinforcement groupRein = null;
        for (Block block : getPatternBlocks(head)) {
            Reinforcement rein = getReinforcement(block);
            if (rein == null) {
                allReinforced = false;
                continue;
            }
            anyReinforced = true;
            if (groupRein == null) {
                groupRein = rein;
            } else if (groupRein.getGroupId() != rein.getGroupId()) {
                event.setCancelled(true);
                return;
            }
        }
        if (!anyReinforced) {
            return;
        }
        if (!allReinforced) {
            event.setCancelled(true);
            return;
        }
        // dispensers and endermen can't be checked for permissions
        UUID builder = getPumpkinPlacer(head);
        if (builder == null) {
            event.setCancelled(true);
            return;
        }
        if (!groupRein.hasPermission(builder, CitadelPermissionHandler.getChests())) {
            event.setCancelled(true);
            Player player = Bukkit.getPlayer(builder);
            if (player != null) {
                CivUI.error(player, "You need chest access on ", CitadelUtility.group(groupRein.getGroup()),
                    " to build a copper golem from its reinforcements");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void tagBuiltGolem(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_COPPERGOLEM) {
            return;
        }
        Block head = event.getLocation().getBlock();
        Reinforcement headRein = getReinforcement(head);
        UUID builder = getPumpkinPlacer(head);
        if (headRein == null || builder == null) {
            return;
        }
        PersistentDataContainer pdc = event.getEntity().getPersistentDataContainer();
        pdc.set(groupKey, PersistentDataType.INTEGER, headRein.getGroupId());
        pdc.set(ownerKey, PersistentDataType.STRING, builder.toString());
        // the pumpkin is consumed, while the copper block turns into a copper chest and keeps its reinforcement
        headRein.setHealth(-1);
    }

    // the owner's chest access is checked live, so a golem loses access together with its owner
    @EventHandler
    public void restrictContainers(ItemTransportingEntityValidateTargetEvent event) {
        if (!(event.getEntity() instanceof CopperGolem golem) || !event.isAllowed()) {
            return;
        }
        // Golems stop moving items between any containers when enabled
        if (!plugin.getConfigManager().copperGolemsEnabled()) {
            event.setAllowed(false);
            return;
        }
        Reinforcement rein = ReinforcementLogic.getReinforcementProtecting(event.getBlock());
        if (rein == null || rein.isInsecure()) {
            return;
        }
        PersistentDataContainer pdc = golem.getPersistentDataContainer();
        Integer golemGroup = pdc.get(groupKey, PersistentDataType.INTEGER);
        String owner = pdc.get(ownerKey, PersistentDataType.STRING);
        if (golemGroup == null || owner == null || golemGroup != rein.getGroupId()
            || !rein.hasPermission(UUID.fromString(owner), CitadelPermissionHandler.getChests())) {
            event.setAllowed(false);
        }
    }

    // a golem turning into a statue only keeps its name, so its group and owner are kept on the statue block
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberStatueGolem(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof CopperGolem golem)
            || !(event.getBlockData() instanceof org.bukkit.block.data.type.CopperGolemStatue)) {
            return;
        }
        PersistentDataContainer golemPdc = golem.getPersistentDataContainer();
        Integer group = golemPdc.get(groupKey, PersistentDataType.INTEGER);
        String owner = golemPdc.get(ownerKey, PersistentDataType.STRING);
        if (group == null || owner == null) {
            return;
        }
        Block block = event.getBlock();
        // the statue's block entity is only created after this event
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(block.getState() instanceof CopperGolemStatue statue)) {
                return;
            }
            statue.getPersistentDataContainer().set(groupKey, PersistentDataType.INTEGER, group);
            statue.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner);
            statue.update();
        });
    }

    // scraping a statue back to life spawns a new golem while the statue block is still there
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void restoreReanimatedGolem(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.REANIMATE
            || !(event.getEntity() instanceof CopperGolem golem)
            || !(event.getLocation().getBlock().getState() instanceof CopperGolemStatue statue)) {
            return;
        }
        PersistentDataContainer statuePdc = statue.getPersistentDataContainer();
        Integer group = statuePdc.get(groupKey, PersistentDataType.INTEGER);
        String owner = statuePdc.get(ownerKey, PersistentDataType.STRING);
        if (group == null || owner == null) {
            return;
        }
        golem.getPersistentDataContainer().set(groupKey, PersistentDataType.INTEGER, group);
        golem.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner);
    }

    private UUID getPumpkinPlacer(Block head) {
        if (lastPumpkinTick != Bukkit.getCurrentTick() || !head.equals(lastPumpkin)) {
            return null;
        }
        return lastPumpkinPlacer;
    }

    /**
     * The golem spawns where the pumpkin was, and the pattern may be built in any direction, so the
     * pattern is the pumpkin plus every block of copper next to it.
     */
    private static List<Block> getPatternBlocks(Block head) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(head);
        for (BlockFace face : FACES) {
            Block body = head.getRelative(face);
            if (COPPER_BLOCKS.contains(body.getType())) {
                blocks.add(body);
            }
        }
        return blocks;
    }

    private static Reinforcement getReinforcement(Block block) {
        return Citadel.getInstance().getReinforcementManager().getReinforcement(block);
    }
}
