package com.github.igotyou.FactoryMod.factories;

import com.github.igotyou.FactoryMod.FactoryMod;
import com.github.igotyou.FactoryMod.FactoryModManager;
import com.github.igotyou.FactoryMod.powerManager.FurnacePowerManager;
import com.github.igotyou.FactoryMod.recipes.EffectFeasibility;
import com.github.igotyou.FactoryMod.recipes.IRecipe;
import com.github.igotyou.FactoryMod.recipes.InputRecipe;
import com.github.igotyou.FactoryMod.recipes.RepairRecipe;
import com.github.igotyou.FactoryMod.repairManager.PercentageHealthRepairManager;
import com.github.igotyou.FactoryMod.structures.FurnCraftChestStructure;
import com.github.igotyou.FactoryMod.utility.Direction;
import com.github.igotyou.FactoryMod.utility.IOSelector;
import com.github.igotyou.FactoryMod.utility.LoggingUtils;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.mockito.Mockito.CALLS_REAL_METHODS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

abstract class FactoryTestSupport {
    protected FactoryMod plugin;
    protected FactoryModManager manager;
    protected PluginManager events;
    private MockedStatic<LoggingUtils> logging;
    protected MockedStatic<Bukkit> bukkit;
    protected BukkitScheduler scheduler;
    private MockedStatic<FactoryMod> factoryMod;

    @BeforeEach
    void setUpServer() {
        MockBukkit.mock();
        logging = mockStatic(LoggingUtils.class);
        bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        factoryMod = mockStatic(FactoryMod.class);
        plugin = mock(FactoryMod.class);
        manager = mock(FactoryModManager.class);
        events = mock(PluginManager.class);
        factoryMod.when(FactoryMod::getInstance).thenReturn(plugin);
        when(plugin.getManager()).thenReturn(manager);
        bukkit.when(Bukkit::getPluginManager).thenReturn(events);
        scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    }

    @AfterEach
    void tearDownServer() {
        factoryMod.close();
        bukkit.close();
        logging.close();
        MockBukkit.unmock();
    }

    protected <T extends InputRecipe> T recipe(Class<T> type, String identifier, String name) {
        T recipe = mock(type);
        when(recipe.getName()).thenReturn(name);
        when(recipe.getIdentifier()).thenReturn(identifier);
        when(recipe.getProductionTime()).thenReturn(10);
        when(recipe.enoughMaterialAvailable(any())).thenReturn(true);
        when(recipe.evaluateEffectFeasibility(any(), any())).thenReturn(new EffectFeasibility(true, null));
        when(recipe.applyEffect(any(), any(), any())).thenReturn(true);
        return recipe;
    }

    protected TestFactory factory(int health, IRecipe... recipes) {
        PercentageHealthRepairManager repairManager = new PercentageHealthRepairManager(health, 100, 0, 1, 1000, 1000);
        FurnacePowerManager power = mock(FurnacePowerManager.class);
        when(power.powerAvailable(anyInt())).thenReturn(true);
        when(power.getPowerConsumptionIntervall()).thenReturn(20);
        FurnCraftChestStructure structure = mock(FurnCraftChestStructure.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location center = new Location(world, 0, 64, 0);
        when(structure.getCenter()).thenReturn(center);
        when(structure.getAllBlocks()).thenReturn(List.of(center));
        when(structure.isComplete()).thenReturn(true);
        TestFactory factory = new TestFactory(repairManager, power, structure, List.of(recipes));
        if (recipes.length != 0) {
            factory.setRecipe(recipes[0]);
        }
        for (IRecipe recipe : recipes) {
            if (recipe instanceof RepairRecipe) {
                when(recipe.applyEffect(any(), any(), any())).thenAnswer(invocation -> {
                    repairManager.repair(25);
                    return true;
                });
            }
        }
        return factory;
    }

    protected static class TestFactory extends FurnCraftChestFactory {
        final Inventory input = mock(Inventory.class);
        final Inventory output = mock(Inventory.class);
        int scheduledUpdates;

        TestFactory(PercentageHealthRepairManager repair, FurnacePowerManager power,
                    FurnCraftChestStructure structure, List<IRecipe> recipes) {
            super(null, repair, power, structure, 1, "Test factory", recipes, 0, 0, 0, 0);
            when(output.getSize()).thenReturn(27);
            setFurnaceIOSelector(new IOSelector());
            IOSelector table = new IOSelector();
            table.setState(Direction.BACK, IOSelector.IOState.BOTH);
            setTableIOSelector(table);
        }

        void completeCycle() {
            setProductionTimer(getCurrentRecipe().getProductionTime());
            run();
        }

        PercentageHealthRepairManager health() {
            return (PercentageHealthRepairManager) getRepairManager();
        }

        @Override
        public Inventory getInputInventory() {
            return input;
        }

        @Override
        public Inventory getOutputInventory() {
            return output;
        }

        @Override
        public void turnFurnaceOn(Block block) {
        }

        @Override
        public void turnFurnaceOff(Block block) {
        }

        @Override
        public void scheduleUpdate() {
            scheduledUpdates++;
        }
    }
}
