package isaac.bastion.listeners;

import isaac.bastion.Bastion;
import isaac.bastion.BastionBlock;
import isaac.bastion.BastionType;
import isaac.bastion.manager.BastionBlockManager;
import isaac.bastion.utils.BastionSettingManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.PermissionType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BastionDamageListenerFertilizeTest {

    private MockedStatic<Bastion> bastionStatic;
    private MockedStatic<PermissionType> permissionStatic;
    private BastionBlockManager blockManager;
    private BastionDamageListener listener;
    private World world;
    private Player player;
    private UUID playerId;
    private PermissionType placePermission;

    @BeforeEach
    void setUp() {
        blockManager = mock(BastionBlockManager.class);
        BastionSettingManager settings = mock(BastionSettingManager.class);

        bastionStatic = Mockito.mockStatic(Bastion.class);
        bastionStatic.when(Bastion::getBastionManager).thenReturn(blockManager);
        bastionStatic.when(Bastion::getSettingManager).thenReturn(settings);

        permissionStatic = Mockito.mockStatic(PermissionType.class);
        placePermission = mock(PermissionType.class);
        permissionStatic.when(() -> PermissionType.getPermission(anyString())).thenReturn(placePermission);

        world = mock(World.class);
        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        listener = new BastionDamageListener();
    }

    @AfterEach
    void tearDown() {
        bastionStatic.close();
        permissionStatic.close();
    }

    private BlockState stateAt(Location location) {
        BlockState state = mock(BlockState.class);
        when(state.getLocation()).thenReturn(location);
        return state;
    }

    private BastionBlock bastion(boolean onlyDirectDestruction) {
        BastionType type = mock(BastionType.class);
        when(type.isOnlyDirectDestruction()).thenReturn(onlyDirectDestruction);
        BastionBlock bastion = mock(BastionBlock.class);
        when(bastion.getType()).thenReturn(type);
        return bastion;
    }

    @Test
    void removesBlocksInsideBlockingBastionField() {
        Location inField = new Location(world, 10, 64, 10);
        Location outside = new Location(world, 100, 64, 100);
        BlockState inFieldState = stateAt(inField);
        BlockState outsideState = stateAt(outside);

        Set<BastionBlock> blocking = Set.of(bastion(false));
        when(blockManager.getBlockingBastionsWithoutPermission(eq(inField), eq(playerId), eq(placePermission)))
            .thenReturn(blocking);
        when(blockManager.getBlockingBastionsWithoutPermission(eq(outside), eq(playerId), eq(placePermission)))
            .thenReturn(Set.of());

        Block clicked = mock(Block.class);
        BlockFertilizeEvent event = new BlockFertilizeEvent(clicked, player,
            new ArrayList<>(List.of(inFieldState, outsideState)));

        listener.onFertilize(event);

        Assertions.assertEquals(List.of(outsideState), event.getBlocks());
        Assertions.assertFalse(event.isCancelled());
    }

    @Test
    void keepsBlocksWhenBastionIsOnlyDirectDestruction() {
        Location inField = new Location(world, 10, 64, 10);
        BlockState state = stateAt(inField);

        Set<BastionBlock> blocking = Set.of(bastion(true));
        when(blockManager.getBlockingBastionsWithoutPermission(eq(inField), eq(playerId), eq(placePermission)))
            .thenReturn(blocking);

        Block clicked = mock(Block.class);
        BlockFertilizeEvent event = new BlockFertilizeEvent(clicked, player, new ArrayList<>(List.of(state)));

        listener.onFertilize(event);

        Assertions.assertEquals(List.of(state), event.getBlocks());
        Assertions.assertFalse(event.isCancelled());
    }

    @Test
    void cancelsDispenserFertilizeEnteringForeignField() {
        Location source = new Location(world, 0, 64, 0);
        Location target = new Location(world, 1, 64, 0);
        BlockState state = stateAt(target);

        Block dispenser = mock(Block.class);
        when(dispenser.getLocation()).thenReturn(source);

        when(blockManager.getEnteredGroupFields(eq(source), any()))
            .thenReturn(Set.of(mock(Group.class)));

        BlockFertilizeEvent event = new BlockFertilizeEvent(dispenser, null, new ArrayList<>(List.of(state)));

        listener.onFertilize(event);

        Assertions.assertTrue(event.isCancelled());
    }
}
