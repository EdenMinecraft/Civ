package com.github.igotyou.FactoryMod.factories;

import com.github.igotyou.FactoryMod.events.FactoryActivateEvent;
import com.github.igotyou.FactoryMod.events.RecipeExecuteEvent;
import com.github.igotyou.FactoryMod.recipes.EffectFeasibility;
import com.github.igotyou.FactoryMod.recipes.InputRecipe;
import com.github.igotyou.FactoryMod.recipes.RepairRecipe;
import com.github.igotyou.FactoryMod.recipes.Upgraderecipe;
import com.github.igotyou.FactoryMod.utility.IOSelector;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import vg.civcraft.mc.citadel.ReinforcementLogic;
import vg.civcraft.mc.citadel.model.Reinforcement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class FurnCraftChestFactoryTest extends FactoryTestSupport {
    private InputRecipe first;
    private InputRecipe preferred;
    private RepairRecipe repair;
    private TestFactory factory;

    @BeforeEach
    void setUpFactory() {
        first = recipe(InputRecipe.class, "first", "First production");
        preferred = recipe(InputRecipe.class, "preferred", "Preferred production");
        repair = recipe(RepairRecipe.class, "repair", "Repair");
        factory = factory(100, first, preferred, repair);
        factory.setRecipeManually(preferred);
        factory.setAutoSelect(true);
    }

    @Test
    void healthyPrefersPlayerChoiceOverFirstInList() {
        assertSame(preferred, factory.getAutoSelectRecipe());
    }

    @Test
    void healthyFallsBackWhenPreferredLacksMaterials() {
        when(preferred.enoughMaterialAvailable(any())).thenReturn(false);
        assertSame(first, factory.getAutoSelectRecipe());
    }

    @Test
    void healthyActivationReplacesSelectedRepairWithoutConsumingRepairMaterials() {
        factory.health().setHealth(25);
        factory.setRecipe(repair);
        factory.attemptToActivate(null, false);
        assertTrue(factory.isActive());
        assertSame(preferred, factory.getCurrentRecipe());
        assertFalse(factory.isAutomaticRepairInProgress());
        verify(repair, never()).applyEffect(any(), any(), any());
    }

    @Test
    void healthyStaysIdleWhenOnlyRepairHasMaterials() {
        when(first.enoughMaterialAvailable(any())).thenReturn(false);
        when(preferred.enoughMaterialAvailable(any())).thenReturn(false);
        factory.setRecipe(repair);
        factory.attemptToActivate(null, false);
        assertFalse(factory.isActive());
        assertNull(factory.getAutoSelectRecipe());
    }

    @Test
    void brokenFactoryRepairsToFullThenResumesPreferredProduction() {
        factory.health().setHealth(0);
        factory.attemptToActivate(null, false);
        assertTrue(factory.isAutomaticRepairInProgress());
        assertSame(repair, factory.getCurrentRecipe());
        for (int i = 0; i < 3; i++) {
            factory.completeCycle();
            assertSame(repair, factory.getCurrentRecipe());
            assertTrue(factory.isActive());
        }
        factory.completeCycle();
        assertTrue(factory.health().atFullHealth());
        assertTrue(factory.isActive());
        assertSame(preferred, factory.getCurrentRecipe());
        assertEquals(0, factory.getRunningTime());
        assertFalse(factory.isAutomaticRepairInProgress());
        assertEquals(4, factory.getRunCount(repair));
        verify(preferred, never()).applyEffect(any(), any(), any());
    }

    @Test
    void factoryThatBreaksWhileRunningSwitchesToRepairBeforeProducing() {
        factory.attemptToActivate(null, false);
        factory.setProductionTimer(preferred.getProductionTime());
        factory.health().setHealth(0);
        factory.run();
        assertSame(repair, factory.getCurrentRecipe());
        assertEquals(0, factory.getRunningTime());
        assertTrue(factory.isAutomaticRepairInProgress());
        verify(preferred, never()).applyEffect(any(), any(), any());
    }

    @Test
    void partialAutomaticRepairSurvivesFuelExhaustion() {
        factory.health().setHealth(0);
        factory.attemptToActivate(null, false);
        when(factory.getPowerManager().powerAvailable(anyInt())).thenReturn(false);
        factory.completeCycle();
        assertEquals(25, factory.health().getRawHealth());
        assertFalse(factory.isActive());
        assertTrue(factory.isAutomaticRepairInProgress());
        when(factory.getPowerManager().powerAvailable(anyInt())).thenReturn(true);
        factory.attemptToActivate(null, false);
        assertTrue(factory.isActive());
        assertSame(repair, factory.getCurrentRecipe());
    }

    @Test
    void partialAutomaticRepairWaitsForRepairMaterials() {
        factory.health().setHealth(0);
        factory.attemptToActivate(null, false);
        factory.completeCycle();
        when(repair.enoughMaterialAvailable(any())).thenReturn(false);
        factory.run();
        assertFalse(factory.isActive());
        assertTrue(factory.isAutomaticRepairInProgress());
        assertNull(factory.getAutoSelectRecipe());
        factory.attemptToActivate(null, false);
        assertFalse(factory.isActive());
        when(repair.enoughMaterialAvailable(any())).thenReturn(true);
        factory.attemptToActivate(null, false);
        assertTrue(factory.isActive());
        assertSame(repair, factory.getCurrentRecipe());
    }

    @Test
    void missingRepairCannotRunProductionInBrokenFactory() {
        factory.health().setHealth(0);
        factory.removeRecipe(repair);
        factory.attemptToActivate(null, false);
        assertFalse(factory.isActive());
        assertNull(factory.getAutoSelectRecipe());
    }

    @Test
    void manualRepairStillRunsAtPartialHealthAndStopsAtFullHealth() {
        factory.setAutoSelect(false);
        factory.health().setHealth(75);
        factory.setRecipeManually(repair);
        factory.attemptToActivate(null, false);
        assertTrue(factory.isActive());
        factory.completeCycle();
        assertTrue(factory.health().atFullHealth());
        assertFalse(factory.isActive());
        assertSame(repair, factory.getCurrentRecipe());
        assertSame(preferred, factory.getPreferredRecipe());
        factory.attemptToActivate(null, false);
        assertFalse(factory.isActive());
    }

    @Test
    void enablingAutoDuringHealthyManualRepairStopsRepairing() {
        factory.setAutoSelect(false);
        factory.health().setHealth(25);
        factory.setRecipeManually(repair);
        factory.attemptToActivate(null, false);
        factory.setAutoSelect(true);
        factory.run();
        assertSame(preferred, factory.getCurrentRecipe());
        verify(repair, never()).applyEffect(any(), any(), any());
    }

    @Test
    void manualProductionSelectionCancelsInterruptedAutomaticRepair() {
        factory.health().setHealth(0);
        factory.attemptToActivate(null, false);
        factory.completeCycle();
        factory.deactivate();
        factory.setRecipeManually(first);
        assertFalse(factory.isAutomaticRepairInProgress());
        factory.attemptToActivate(null, false);
        assertSame(first, factory.getCurrentRecipe());
        assertSame(first, factory.getPreferredRecipe());
    }

    @Test
    void removedAndRepairRecipesCannotBecomePreferences() {
        factory.setPreferredRecipe(repair);
        assertNull(factory.getPreferredRecipe());
        factory.setPreferredRecipe(preferred);
        factory.removeRecipe(preferred);
        assertNull(factory.getPreferredRecipe());
        factory.setRecipeManually(preferred);
        assertNull(factory.getPreferredRecipe());
        assertSame(first, factory.getAutoSelectRecipe());
    }

    @Test
    void automaticFallbackDoesNotInheritProductionProgress() {
        factory.attemptToActivate(null, false);
        factory.setProductionTimer(9);
        when(preferred.enoughMaterialAvailable(any())).thenReturn(false);
        factory.run();
        assertSame(first, factory.getCurrentRecipe());
        assertEquals(0, factory.getRunningTime());
        verify(first, never()).applyEffect(any(), any(), any());
    }

    @Test
    void completedRepairStopsIfProductionActivationIsCancelled() {
        factory.health().setHealth(0);
        factory.attemptToActivate(null, false);
        factory.health().setHealth(75);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof FactoryActivateEvent activation) {
                activation.setCancelled(true);
            }
            return null;
        }).when(events).callEvent(any());
        factory.completeCycle();
        assertFalse(factory.isActive());
        assertFalse(factory.isAutomaticRepairInProgress());
        verify(preferred, never()).applyEffect(any(), any(), any());
    }

    @Test
    void completedRepairChecksProductionEffectFeasibility() {
        factory.health().setHealth(0);
        factory.attemptToActivate(null, false);
        factory.health().setHealth(75);
        when(preferred.evaluateEffectFeasibility(any(), any())).thenReturn(new EffectFeasibility(false, "blocked"));
        factory.completeCycle();
        assertFalse(factory.isActive());
        verify(preferred, never()).applyEffect(any(), any(), any());
    }

    @Test
    void cancelledRepairExecutionDoesNotHealOrForgetAutomaticRepair() {
        factory.health().setHealth(0);
        factory.attemptToActivate(null, false);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof RecipeExecuteEvent execution) {
                execution.setCancelled(true);
            }
            return null;
        }).when(events).callEvent(any());
        factory.completeCycle();
        assertFalse(factory.isActive());
        assertEquals(0, factory.health().getRawHealth());
        assertTrue(factory.isAutomaticRepairInProgress());
    }

    @Test
    void redstoneActivationWithNoOutputsFailsWithoutThrowing() {
        factory.setTableIOSelector(new IOSelector());
        assertDoesNotThrow(() -> factory.attemptToActivate(null, false));
        assertFalse(factory.isActive());
    }

    @Test
    void startupRecipeChangeDoesNotBypassUpgradePermission() {
        Upgraderecipe upgrade = recipe(Upgraderecipe.class, "upgrade", "Upgrade");
        factory = factory(100, upgrade, repair);
        factory.setRecipe(repair);
        factory.setAutoSelect(true);
        when(manager.isCitadelEnabled()).thenReturn(true);
        try (MockedStatic<ReinforcementLogic> citadel = mockStatic(ReinforcementLogic.class)) {
            citadel.when(() -> ReinforcementLogic.getReinforcementAt(any(Location.class)))
                .thenReturn(mock(Reinforcement.class));
            factory.attemptToActivate(null, true);
            assertFalse(factory.isActive());
            verify(upgrade, never()).applyEffect(any(), any(), any());
        }
    }
}
