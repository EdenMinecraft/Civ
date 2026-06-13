package com.github.igotyou.FactoryMod.factories;

import com.github.igotyou.FactoryMod.recipes.IRecipe;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class ChooseAutoRecipeTest {

    private final IRecipe production1 = mock(IRecipe.class);
    private final IRecipe production2 = mock(IRecipe.class);
    private final IRecipe repair = mock(IRecipe.class);

    private final Predicate<IRecipe> isRepair = r -> r == repair;

    private static Predicate<IRecipe> hasMaterials(IRecipe... withMaterials) {
        Set<IRecipe> set = Set.of(withMaterials);
        return set::contains;
    }

    @Test
    void healthyPrefersPlayerChoiceOverFirstInList() {
        IRecipe chosen = FurnCraftChestFactory.chooseAutoRecipe(
            List.of(production1, production2, repair), false, production2,
            isRepair, hasMaterials(production1, production2));
        assertSame(production2, chosen);
    }

    @Test
    void healthyFallsBackToFirstAvailableWhenPreferredLacksMaterials() {
        IRecipe chosen = FurnCraftChestFactory.chooseAutoRecipe(
            List.of(production1, production2, repair), false, production2,
            isRepair, hasMaterials(production1));
        assertSame(production1, chosen);
    }

    @Test
    void healthyNeverRunsRepairEvenWhenCurrentlySelected() {
        IRecipe chosen = FurnCraftChestFactory.chooseAutoRecipe(
            List.of(production1, repair), false, production1,
            isRepair, hasMaterials(production1, repair));
        assertSame(production1, chosen);
    }

    @Test
    void healthyStaysIdleWhenNoProductionHasMaterials() {
        IRecipe chosen = FurnCraftChestFactory.chooseAutoRecipe(
            List.of(production1, repair), false, production1,
            isRepair, hasMaterials(repair));
        assertNull(chosen);
    }

    @Test
    void brokenRunsRepairRegardlessOfPreferred() {
        IRecipe chosen = FurnCraftChestFactory.chooseAutoRecipe(
            List.of(production1, repair), true, production1,
            isRepair, hasMaterials(production1, repair));
        assertSame(repair, chosen);
    }

    @Test
    void brokenWithNoRepairRecipeReturnsNull() {
        IRecipe chosen = FurnCraftChestFactory.chooseAutoRecipe(
            List.of(production1, production2), true, null,
            isRepair, hasMaterials(production1, production2));
        assertNull(chosen);
    }
}
