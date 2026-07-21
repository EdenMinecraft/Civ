package isaac.bastion;

import isaac.bastion.storage.BastionBlockStorage;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import vg.civcraft.mc.citadel.Citadel;
import vg.civcraft.mc.citadel.ReinforcementManager;
import vg.civcraft.mc.citadel.model.Reinforcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BastionBlockCompareToTest {

    private MockedStatic<Citadel> citadelStatic;
    private MockedStatic<Bastion> bastionStatic;
    private BastionType type;

    // Locations hold their World via WeakReference; keep strong refs so getWorld() stays valid.
    private final UUID worldUidLow = new UUID(0L, 1L);
    private final UUID worldUidHigh = new UUID(0L, 2L);
    private World worldLow;
    private World worldHigh;

    @BeforeEach
    void setUp() {
        worldLow = mockWorld(worldUidLow);
        worldHigh = mockWorld(worldUidHigh);

        type = mock(BastionType.class);
        when(type.getWarmupTime()).thenReturn(0L);

        Citadel citadel = mock(Citadel.class);
        ReinforcementManager reinforcementManager = mock(ReinforcementManager.class);
        Reinforcement reinforcement = mock(Reinforcement.class);
        when(reinforcement.getGroupId()).thenReturn(1);
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(reinforcement);
        when(citadel.getReinforcementManager()).thenReturn(reinforcementManager);

        citadelStatic = Mockito.mockStatic(Citadel.class);
        citadelStatic.when(Citadel::getInstance).thenReturn(citadel);

        Bastion plugin = mock(Bastion.class);
        BastionBlockStorage storage = mock(BastionBlockStorage.class);
        bastionStatic = Mockito.mockStatic(Bastion.class);
        bastionStatic.when(Bastion::getPlugin).thenReturn(plugin);
        bastionStatic.when(Bastion::getBastionStorage).thenReturn(storage);
    }

    @AfterEach
    void tearDown() {
        try {
            citadelStatic.close();
        } finally {
            bastionStatic.close();
        }
    }

    private World mockWorld(UUID uid) {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
        when(world.getUID()).thenReturn(uid);
        return world;
    }

    private BastionBlock block(World world, int x, int y, int z) {
        return new BastionBlock(new Location(world, x, y, z), 0L, 1, type);
    }

    @Test
    void ordersBlocksByWorldUidWhenCoordsAreIdentical() {
        BastionBlock low = block(worldLow, 10, 64, 20);
        BastionBlock high = block(worldHigh, 10, 64, 20);

        Assertions.assertTrue(low.compareTo(high) < 0,
            "block in lower world UID must sort before identical coords in higher world UID");
        Assertions.assertTrue(high.compareTo(low) > 0,
            "block in higher world UID must sort after identical coords in lower world UID");
        Assertions.assertNotEquals(0, low.compareTo(high),
            "blocks in different worlds with identical coords must not compare equal");
        Assertions.assertNotEquals(low, high,
            "blocks in different worlds are distinct instances and not equal");
    }

    @Test
    void compareToIsAntisymmetricAcrossWorldsAndCoords() {
        BastionBlock[] blocks = {
            block(worldLow, 0, 0, 0),
            block(worldLow, 0, 0, 5),
            block(worldLow, 0, 7, 0),
            block(worldLow, 3, 0, 0),
            block(worldHigh, 0, 0, 0),
            block(worldHigh, 3, 7, 5),
            block(worldHigh, -4, -2, -8),
        };

        for (BastionBlock a : blocks) {
            for (BastionBlock b : blocks) {
                Assertions.assertEquals(
                    Integer.signum(a.compareTo(b)),
                    -Integer.signum(b.compareTo(a)),
                    "compareTo must be antisymmetric for every pair");
            }
        }
    }
}
