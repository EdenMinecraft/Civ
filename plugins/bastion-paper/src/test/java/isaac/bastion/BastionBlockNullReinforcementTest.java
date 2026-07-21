package isaac.bastion;

import isaac.bastion.storage.BastionBlockStorage;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BastionBlockNullReinforcementTest {

    private MockedStatic<Citadel> citadelStatic;
    private MockedStatic<Bastion> bastionStatic;
    private ReinforcementManager reinforcementManager;
    private BastionBlockStorage storage;
    // Location holds World via WeakReference, so a local would GC and getWorld() would throw.
    private World world;
    private Location location;
    private BastionType type;
    private Reinforcement liveReinforcement;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
        location = new Location(world, 10, 64, 20);

        type = mock(BastionType.class);
        when(type.isDestroyOnRemove()).thenReturn(true);
        when(type.isDestroyOnRemoveWhileImmature()).thenReturn(true);
        when(type.getWarmupTime()).thenReturn(0L);

        Citadel citadel = mock(Citadel.class);
        reinforcementManager = mock(ReinforcementManager.class);
        when(citadel.getReinforcementManager()).thenReturn(reinforcementManager);

        citadelStatic = Mockito.mockStatic(Citadel.class);
        citadelStatic.when(Citadel::getInstance).thenReturn(citadel);

        Bastion plugin = mock(Bastion.class);
        storage = mock(BastionBlockStorage.class);

        bastionStatic = Mockito.mockStatic(Bastion.class);
        bastionStatic.when(Bastion::getPlugin).thenReturn(plugin);
        bastionStatic.when(Bastion::getBastionStorage).thenReturn(storage);

        liveReinforcement = mock(Reinforcement.class);
        when(liveReinforcement.getGroupId()).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        try {
            citadelStatic.close();
        } finally {
            bastionStatic.close();
        }
    }

    private BastionBlock constructWithLiveReinforcement() {
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(liveReinforcement);
        return new BastionBlock(location, 0L, 1, type);
    }

    @Test
    void shouldCull_returnsFalseOnNull() {
        BastionBlock bastion = constructWithLiveReinforcement();
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(null);

        Assertions.assertFalse(bastion.shouldCull(),
            "shouldCull must not flag bastions for culling on transient null lookups");
        verify(storage, never()).deleteBastion(bastion);
        verify(storage, never()).setBastionAsDead(bastion);
    }

    @Test
    void shouldCull_returnsTrueOnNegativeHealth() {
        BastionBlock bastion = constructWithLiveReinforcement();

        Reinforcement dyingReinforcement = mock(Reinforcement.class);
        when(dyingReinforcement.getGroupId()).thenReturn(1);
        when(dyingReinforcement.getHealth()).thenReturn(-1.0f);
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(dyingReinforcement);

        Assertions.assertTrue(bastion.shouldCull(),
            "shouldCull must still flag bastions whose health has been driven below zero");
    }

    @Test
    void shouldCull_returnsTrueOnZeroHealth() {
        BastionBlock bastion = constructWithLiveReinforcement();

        Reinforcement brokenReinforcement = mock(Reinforcement.class);
        when(brokenReinforcement.getGroupId()).thenReturn(1);
        when(brokenReinforcement.getHealth()).thenReturn(0.0f);
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(brokenReinforcement);

        Assertions.assertTrue(bastion.shouldCull(),
            "shouldCull must treat health of exactly 0 as broken to match Citadel.isBroken() semantics");
    }

    @Test
    void getReinforcement_doesNotDestroyOnNull() {
        BastionBlock bastion = constructWithLiveReinforcement();
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(null);

        Assertions.assertNull(bastion.getReinforcement());
        verify(storage, never()).deleteBastion(bastion);
        verify(storage, never()).setBastionAsDead(bastion);
    }

    @Test
    void regen_doesNotDestroyOnNull() {
        BastionBlock bastion = constructWithLiveReinforcement();
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(null);

        bastion.regen();

        verify(storage, never()).deleteBastion(bastion);
        verify(storage, never()).setBastionAsDead(bastion);
    }

    @Test
    void constructor_doesNotDestroyOnNull() {
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(null);

        BastionBlock bastion = new BastionBlock(location, 0L, 1, type);

        Assertions.assertNotNull(bastion);
        verify(storage, never()).deleteBastion(bastion);
        verify(storage, never()).setBastionAsDead(bastion);
    }

    @Test
    void erode_doesNotDestroyOnNull() {
        BastionBlock bastion = constructWithLiveReinforcement();
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(null);

        bastion.erode(1.0);

        verify(storage, never()).deleteBastion(bastion);
        verify(storage, never()).setBastionAsDead(bastion);
    }

    @Test
    void getListGroupId_backfillsLazilyAndCachesAfterFirstCall() {
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(null);
        BastionBlock bastion = new BastionBlock(location, 0L, 1, type);

        Assertions.assertNull(bastion.getListGroupId(),
            "listGroupId stays null while reinforcement is unavailable");

        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(liveReinforcement);

        Assertions.assertEquals(1, bastion.getListGroupId(),
            "First call after the reinforcement becomes available backfills the groupId");
        Assertions.assertEquals(1, bastion.getListGroupId(),
            "Subsequent calls return the cached value");

        verify(storage, Mockito.times(1)).indexBastionByGroup(bastion, 1);
    }

    @Test
    void regen_doesNotMarkDeadOnNullForKeepOnDeathType() {
        BastionType keepOnDeath = mock(BastionType.class);
        when(keepOnDeath.isDestroyOnRemove()).thenReturn(false);
        when(keepOnDeath.isDestroyOnRemoveWhileImmature()).thenReturn(false);
        when(keepOnDeath.getWarmupTime()).thenReturn(0L);

        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(liveReinforcement);
        BastionBlock bastion = new BastionBlock(location, 0L, 1, keepOnDeath);
        when(reinforcementManager.getReinforcement(any(Location.class))).thenReturn(null);

        bastion.regen();

        verify(storage, never()).deleteBastion(bastion);
        verify(storage, never()).setBastionAsDead(bastion);
    }
}
