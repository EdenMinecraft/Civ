package com.github.igotyou.FactoryMod.factories;

import com.github.igotyou.FactoryMod.FactoryModManager;
import com.github.igotyou.FactoryMod.FactoryModPlayerSettings;
import com.github.igotyou.FactoryMod.eggs.FurnCraftChestEgg;
import com.github.igotyou.FactoryMod.recipes.InputRecipe;
import com.github.igotyou.FactoryMod.recipes.RepairRecipe;
import com.github.igotyou.FactoryMod.utility.Direction;
import com.github.igotyou.FactoryMod.utility.FileHandler;
import com.github.igotyou.FactoryMod.utility.IOSelector;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.mockito.MockedConstruction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FactoryPersistenceTest extends FactoryTestSupport {
    @TempDir Path directory;
    private InputRecipe production;
    private RepairRecipe repair;
    private FurnCraftChestEgg egg;
    private FileHandler files;
    private File saveFile;

    @BeforeEach
    void setUpPersistence() {
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        production = recipe(InputRecipe.class, "production_id", "Production");
        repair = recipe(RepairRecipe.class, "repair_id", "Repair");
        egg = mock(FurnCraftChestEgg.class);
        when(egg.getRecipes()).thenReturn(List.of(production, repair));
        files = new FileHandler(manager, Map.of());
        saveFile = directory.resolve("factoryData.yml").toFile();
    }

    @Test
    void partialAutomaticRepairRoundTripsAndResumesBeforeProduction() {
        TestFactory original = factory(0, production, repair);
        original.setRecipeManually(production);
        original.setAutoSelect(true);
        original.attemptToActivate(null, false);
        original.completeCycle();
        original.setProductionTimer(5);
        files.save(files.snapshot(List.of(original)));

        TestFactory restored = prepareRestore(25, repair);
        files.load(Map.of("test factory", egg));
        assertTrue(restored.isActive());
        assertTrue(restored.isAutomaticRepairInProgress());
        assertSame(production, restored.getPreferredRecipe());
        assertSame(repair, restored.getCurrentRecipe());
        assertEquals(6, restored.getRunningTime());
        assertEquals(1, restored.getRunCount(repair));
        for (int i = 0; i < 3; i++) {
            restored.completeCycle();
        }
        assertTrue(restored.health().atFullHealth());
        assertSame(production, restored.getCurrentRecipe());
    }

    @Test
    void activeFactoryWithZeroProgressRestartsAtRecipeBoundary() {
        TestFactory original = factory(0, production, repair);
        original.setAutoSelect(true);
        original.attemptToActivate(null, false);
        original.completeCycle();
        assertEquals(0, original.getRunningTime());
        files.save(files.snapshot(List.of(original)));
        TestFactory restored = prepareRestore(25, repair);
        files.load(Map.of("test factory", egg));
        assertTrue(restored.isActive());
        assertEquals(1, restored.getRunningTime());
    }

    @Test
    void interruptedRepairRemainsIdleAfterLoadAndCanResume() {
        TestFactory original = factory(0, production, repair);
        original.setRecipeManually(production);
        original.setAutoSelect(true);
        original.attemptToActivate(null, false);
        original.completeCycle();
        original.deactivate();
        files.save(files.snapshot(List.of(original)));
        TestFactory restored = prepareRestore(25, repair);
        files.load(Map.of("test factory", egg));
        assertFalse(restored.isActive());
        assertTrue(restored.isAutomaticRepairInProgress());
        restored.attemptToActivate(null, false);
        assertSame(repair, restored.getCurrentRecipe());
        assertTrue(restored.isActive());
    }

    @Test
    void renamedDisplayNameRetainsPreferenceThroughIdentifier() {
        TestFactory original = factory(100, production, repair);
        original.setRecipeManually(production);
        files.save(files.snapshot(List.of(original)));
        when(production.getName()).thenReturn("Renamed production");
        TestFactory restored = prepareRestore(100, production);
        files.load(Map.of("test factory", egg));
        assertSame(production, restored.getPreferredRecipe());
    }

    @Test
    void oldSavesInferPreferenceFromSelectedProduction() throws Exception {
        writeLegacySave("Production", 0, false);
        TestFactory restored = prepareRestore(100, production);
        files.load(Map.of("test factory", egg));
        assertSame(production, restored.getPreferredRecipe());
        assertFalse(restored.isActive());
    }

    @Test
    void oldRunningRepairSaveContinuesRepairing() throws Exception {
        writeLegacySave("Repair", 5, true);
        TestFactory restored = prepareRestore(25, repair);
        files.load(Map.of("test factory", egg));
        assertTrue(restored.isAutomaticRepairInProgress());
        assertSame(repair, restored.getCurrentRecipe());
        assertTrue(restored.isActive());
        assertEquals(6, restored.getRunningTime());
    }

    @Test
    void oldPreferenceNameStillLoadsButUnknownIdentifierIsIgnored() throws Exception {
        writeLegacySave("Repair", 0, true);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(saveFile);
        config.set("factory.preferredRecipe", "Production");
        config.save(saveFile);
        TestFactory restored = prepareRestore(25, repair);
        files.load(Map.of("test factory", egg));
        assertSame(production, restored.getPreferredRecipe());

        config.set("factory.preferredRecipeId", "removed_recipe");
        config.save(saveFile);
        restored = prepareRestore(25, repair);
        files.load(Map.of("test factory", egg));
        assertNull(restored.getPreferredRecipe());
    }

    @Test
    void replacementRecipeDoesNotInheritSavedProgress() throws Exception {
        writeLegacySave("Repair", 9, true);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(saveFile);
        config.set("factory.automaticRepairInProgress", false);
        config.save(saveFile);
        TestFactory restored = prepareRestore(100, repair);
        files.load(Map.of("test factory", egg));
        assertSame(production, restored.getCurrentRecipe());
        assertEquals(1, restored.getRunningTime());
        verify(production, never()).applyEffect(any(), any(), any());
    }

    @Test
    void restartRestoresRoutingAndCountersBeforeActivation() throws Exception {
        writeLegacySave("Production", 5, true);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(saveFile);
        IOSelector customRouting = new IOSelector();
        customRouting.setState(Direction.TOP, IOSelector.IOState.BOTH);
        UUID activator = UUID.randomUUID();
        config.set("factory.furnace-io", customRouting.toConfigSection());
        config.set("factory.table-io", new IOSelector().toConfigSection());
        config.set("factory.recipeLevels.Production", 3);
        config.set("factory.runcounts.Production", 7);
        config.set("factory.activator", activator.toString());
        config.save(saveFile);
        TestFactory restored = prepareRestore(100, production);
        when(production.enoughMaterialAvailable(any())).thenAnswer(invocation -> {
            assertTrue(restored.isAutoSelect());
            assertSame(production, restored.getPreferredRecipe());
            assertTrue(restored.getFurnaceIOSelector().isOutput(Direction.TOP));
            assertEquals(0, restored.getTableIOSelector().getOutputCount());
            assertEquals(3, restored.getRecipeLevel(production));
            assertEquals(7, restored.getRunCount(production));
            assertEquals(activator, restored.getActivator());
            return true;
        });
        files.load(Map.of("test factory", egg));
        assertTrue(restored.isActive());
    }

    @Test
    void restartWithNoOutputsRemainsIdle() throws Exception {
        writeLegacySave("Production", 5, true);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(saveFile);
        config.set("factory.furnace-io", new IOSelector().toConfigSection());
        config.set("factory.table-io", new IOSelector().toConfigSection());
        config.save(saveFile);
        TestFactory restored = prepareRestore(100, production);
        assertDoesNotThrow(() -> files.load(Map.of("test factory", egg)));
        assertFalse(restored.isActive());
    }

    @Test
    void olderAsyncSnapshotCannotOverwriteNewerShutdownSave() {
        TestFactory original = factory(0, production, repair);
        original.setAutoSelect(true);
        original.attemptToActivate(null, false);
        FileHandler.SaveSnapshot older = files.snapshot(List.of(original));
        for (int i = 0; i < 4; i++) {
            original.completeCycle();
        }
        FileHandler.SaveSnapshot newer = files.snapshot(List.of(original));
        files.save(newer);
        files.save(older);
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(saveFile);
        assertEquals(100, saved.getInt("world#0#64#0.health"));
        assertEquals("Production", saved.getString("world#0#64#0.selectedRecipe"));
        assertFalse(saved.getBoolean("world#0#64#0.automaticRepairInProgress"));
        assertFalse(directory.resolve("factoryDataPreviousSave.yml").toFile().exists());
    }

    @Test
    void snapshotKeepsRepairStateTogetherDespiteLaterFactoryChanges() {
        TestFactory original = factory(0, production, repair);
        original.setAutoSelect(true);
        original.attemptToActivate(null, false);
        FileHandler.SaveSnapshot snapshot = files.snapshot(List.of(original));
        for (int i = 0; i < 4; i++) {
            original.completeCycle();
        }
        files.save(snapshot);
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(saveFile);
        assertEquals(0, saved.getInt("world#0#64#0.health"));
        assertEquals("Repair", saved.getString("world#0#64#0.selectedRecipe"));
        assertTrue(saved.getBoolean("world#0#64#0.automaticRepairInProgress"));
    }

    @Test
    void failedSnapshotLeavesLastSaveIntact() throws Exception {
        TestFactory original = factory(100, production, repair);
        files.save(files.snapshot(List.of(original)));
        String saved = Files.readString(saveFile.toPath());
        FurnCraftChestFactory broken = mock(FurnCraftChestFactory.class);
        when(broken.getMultiBlockStructure()).thenThrow(new IllegalStateException("snapshot failed"));
        assertThrows(IllegalStateException.class, () -> files.snapshot(List.of(broken)));
        assertEquals(saved, Files.readString(saveFile.toPath()));
    }

    @Test
    void failedWritePreservesPrimaryAndRemovesTemporaryFile() throws Exception {
        TestFactory original = factory(100, production, repair);
        files.save(files.snapshot(List.of(original)));
        String saved = Files.readString(saveFile.toPath());
        Path backupDirectory = Files.createDirectory(directory.resolve("factoryDataPreviousSave.yml"));
        Files.writeString(backupDirectory.resolve("blocked"), "backup is unavailable");
        original.health().setHealth(0);
        files.save(files.snapshot(List.of(original)));
        assertEquals(saved, Files.readString(saveFile.toPath()));
        try (var children = Files.list(directory)) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().startsWith("factoryData-")));
        }
        verify(plugin).severe("Fatal error while trying to save factory data");
    }

    @Test
    void missingPrimaryLoadsPreviousSave() throws Exception {
        TestFactory original = factory(100, production, repair);
        original.setRecipeManually(production);
        files.save(files.snapshot(List.of(original)));
        Files.move(saveFile.toPath(), directory.resolve("factoryDataPreviousSave.yml"));
        TestFactory restored = prepareRestore(100, production);
        files.load(Map.of("test factory", egg));
        assertSame(production, restored.getPreferredRecipe());
        verify(manager).addFactory(restored);
    }

    @Test
    void asyncSaveCollectsFactoriesOnServerThreadAndWritesAfterSnapshot() throws Exception {
        try (MockedConstruction<FactoryModPlayerSettings> settings = mockConstruction(FactoryModPlayerSettings.class)) {
            FactoryModManager savingManager = spy(new FactoryModManager(plugin, Material.STICK, false, false,
                1, 1, false, 1, 1, 1, 3, Map.of(), false));
            savingManager.addFactory(factory(100, production, repair));
            AtomicBoolean capturing = new AtomicBoolean();
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            doAnswer(invocation -> {
                assertTrue(capturing.get(), "Factories must be collected inside the server-thread task");
                return invocation.callRealMethod();
            }).when(savingManager).getAllFactories();
            when(scheduler.callSyncMethod(eq(plugin), any())).thenAnswer(invocation -> {
                Callable<FileHandler.SaveSnapshot> capture = invocation.getArgument(1);
                capturing.set(true);
                FileHandler.SaveSnapshot snapshot = capture.call();
                capturing.set(false);
                assertFalse(saveFile.exists(), "Disk writes must happen after returning to the async caller");
                return CompletableFuture.completedFuture(snapshot);
            });
            savingManager.saveFactories();
            assertTrue(saveFile.exists());
            verify(scheduler).callSyncMethod(eq(plugin), any());
        }
    }

    private TestFactory prepareRestore(int health, InputRecipe selected) {
        TestFactory restored = factory(health, production, repair);
        restored.setRecipe(selected);
        when(egg.revive(anyList(), anyInt(), anyString(), anyLong(), anyList(), anyInt(), anyInt(), anyInt()))
            .thenReturn(restored);
        return restored;
    }

    private void writeLegacySave(String selected, int runtime, boolean autoSelect) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("version", 2);
        ConfigurationSection saved = config.createSection("factory");
        saved.set("type", "FCC");
        saved.set("name", "Test factory");
        saved.set("selectedRecipe", selected);
        saved.set("runtime", runtime);
        saved.set("autoSelect", autoSelect);
        saved.set("health", 25);
        saved.set("recipes", List.of("production_id", "repair_id"));
        saved.createSection("blocks");
        config.save(saveFile);
    }
}
