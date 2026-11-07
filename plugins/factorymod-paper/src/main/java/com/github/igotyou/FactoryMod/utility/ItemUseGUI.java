package com.github.igotyou.FactoryMod.utility;

import com.github.igotyou.FactoryMod.FactoryMod;
import com.github.igotyou.FactoryMod.eggs.FurnCraftChestEgg;
import com.github.igotyou.FactoryMod.eggs.IFactoryEgg;
import com.github.igotyou.FactoryMod.recipes.CompactingRecipe;
import com.github.igotyou.FactoryMod.recipes.IRecipe;
import com.github.igotyou.FactoryMod.recipes.InputRecipe;
import com.github.igotyou.FactoryMod.recipes.ProductionRecipe;
import com.github.igotyou.FactoryMod.recipes.RandomOutputRecipe;
import com.github.igotyou.FactoryMod.recipes.VariantRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import vg.civcraft.mc.civmodcore.inventory.CustomItem;
import vg.civcraft.mc.civmodcore.inventory.items.ItemMap;
import vg.civcraft.mc.civmodcore.inventory.gui.IClickable;
import vg.civcraft.mc.civmodcore.inventory.gui.LClickable;
import vg.civcraft.mc.civmodcore.inventory.gui.components.ComponableInventory;
import vg.civcraft.mc.civmodcore.inventory.gui.components.ContentAligners;
import vg.civcraft.mc.civmodcore.inventory.gui.components.Scrollbar;
import vg.civcraft.mc.civmodcore.inventory.gui.components.SlotPredicates;
import vg.civcraft.mc.civmodcore.inventory.gui.components.StaticDisplaySection;
import vg.civcraft.mc.civmodcore.inventory.items.ItemUtils;
import vg.civcraft.mc.civmodcore.ui.CivText;
import com.github.igotyou.FactoryMod.utility.FMText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class ItemUseGUI {

    /** Components that vary between copies of the same produced item, so a lookup ignores them */
    private static final List<DataComponentType> IGNORED_COMPONENTS = List.of(DataComponentTypes.ENCHANTMENTS,
        DataComponentTypes.STORED_ENCHANTMENTS, DataComponentTypes.DAMAGE, DataComponentTypes.REPAIR_COST);

    private Player player;
    private ComponableInventory inventory;

    public ItemUseGUI(Player player) {
        this.player = player;
    }

    /**
     * Shows an interactive menu containing all factory recipes which take or produce a specified item
     *
     * @param item
     */
    public void showItemOverview(ItemStack item) {
        if (inventory == null) {
            inventory = new ComponableInventory(FMText.title("Used In          Made By"), 6, player);
        }
        FactoryModGUI gui = new FactoryModGUI(player);
        List<IClickable> itemAsInput = new ArrayList<>();
        List<IClickable> itemAsOutput = new ArrayList<>();
        List<IFactoryEgg> eggList = new ArrayList<>(FactoryMod.getInstance().getManager().getAllFactoryEggs());
        for (IFactoryEgg egg : eggList) {
            if (!(egg instanceof FurnCraftChestEgg)) {
                continue;
            }
            FurnCraftChestEgg fccEgg = (FurnCraftChestEgg) egg;

            ItemStack getItemAsSetup = getItemAsSetup(fccEgg, item);
            if (getItemAsSetup != null) {
                itemAsInput.add(new LClickable(getItemAsSetup, p -> {
                    gui.showForFactory(fccEgg);
                }));
            }

            for (IRecipe recipe : fccEgg.getRecipes()) {
                if (!(recipe instanceof InputRecipe)) {
                    continue;
                }
                InputRecipe inputRecipe = (InputRecipe) recipe;

                ItemStack getItemAsInput = getItemAsInput(fccEgg, inputRecipe, item);
                if (getItemAsInput != null) {
                    itemAsInput.add(new LClickable(getItemAsInput, p -> {
                        gui.showForFactory(fccEgg, inputRecipe);
                    }));
                }
                ItemStack getItemAsOutput = getItemAsOutput(fccEgg, inputRecipe, item);
                if (getItemAsOutput != null) {
                    itemAsOutput.add(new LClickable(getItemAsOutput, p -> {
                        gui.showForFactory(fccEgg, inputRecipe);
                    }));
                }
            }
        }
        if (itemAsInput.isEmpty()) {
            ItemStack noItems = new ItemStack(Material.BARRIER);
            FMText.name(noItems, Component.text().append(Component.text("No recipe uses ", CivText.ERROR)).append(FMText.item(item)).build());
            itemAsInput.add(new LClickable(noItems, p -> {
            }));
        }
        if (itemAsOutput.isEmpty()) {
            ItemStack noItems = new ItemStack(Material.BARRIER);
            FMText.name(noItems, Component.text().append(Component.text("No recipe makes ", CivText.ERROR)).append(FMText.item(item)).build());
            itemAsOutput.add(new LClickable(noItems, p -> {
            }));
        }
        Scrollbar itemAsInputBar = new Scrollbar(itemAsInput, 24, 8, ContentAligners
            .getCenteredInOrder(itemAsInput.size(), 24, 4));
        itemAsInputBar.setBackwardsClickSlot(3);
        inventory.addComponent(itemAsInputBar, SlotPredicates.rectangle(6, 4));

        IClickable dividerClick = getDividerClick();
        StaticDisplaySection
            middleLine = new StaticDisplaySection(dividerClick, dividerClick, dividerClick, dividerClick, dividerClick, dividerClick);
        inventory.addComponent(middleLine, SlotPredicates.offsetRectangle(6, 1, 0, 4));

        Scrollbar itemAsOutputBar = new Scrollbar(itemAsOutput, 24, 5, ContentAligners.getCenteredInOrder(itemAsOutput.size(), 24, 4));
        inventory.addComponent(itemAsOutputBar, SlotPredicates.offsetRectangle(6, 4, 0, 5));
        inventory.show();
    }

    /**
     * All custom and named items used by or produced in factories, keyed by their lookup name, e.g. "player_essence"
     */
    public static Map<String, ItemStack> getNamedRecipeItems() {
        Map<String, ItemStack> result = new TreeMap<>();
        for (IFactoryEgg egg : FactoryMod.getInstance().getManager().getAllFactoryEggs()) {
            if (!(egg instanceof FurnCraftChestEgg fccEgg)) {
                continue;
            }
            if (fccEgg.getSetupCost() != null) {
                addNamedItems(result, fccEgg.getSetupCost());
            }
            for (IRecipe recipe : fccEgg.getRecipes()) {
                if (recipe instanceof InputRecipe inputRecipe) {
                    addNamedItems(result, inputRecipe.getInput());
                }
                if (recipe instanceof ProductionRecipe productionRecipe) {
                    addNamedItems(result, productionRecipe.getOutput());
                }
                if (recipe instanceof RandomOutputRecipe randomRecipe) {
                    for (Map.Entry<ItemMap, Double> entry : randomRecipe.getOutputs().entrySet()) {
                        if (entry.getValue() != null && entry.getValue() > 0) {
                            addNamedItems(result, entry.getKey());
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void addNamedItems(Map<String, ItemStack> result, ItemMap items) {
        for (ItemStack item : items.getAllItems().keySet()) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            Component name = meta.hasDisplayName() ? meta.displayName()
                : CustomItem.isCustomItem(item) && meta.hasItemName() ? meta.itemName() : null;
            if (name != null) {
                result.putIfAbsent(toLookupName(PlainTextComponentSerializer.plainText().serialize(name)), item.asOne());
            }
        }
    }

    private static final Pattern LEGACY_FORMAT_CODES = Pattern.compile("\u00a7[0-9a-fk-orA-FK-OR]");

    /** "Player Essence" to "player_essence", so it can be typed as a single command argument */
    public static String toLookupName(String name) {
        String stripped = LEGACY_FORMAT_CODES.matcher(name).replaceAll("");
        return stripped.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private IClickable getDividerClick() {
        ItemStack is = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        FMText.name(is, Component.text("Divider", CivText.VALUE));
        return new LClickable(is, p -> {
        });
    }

    private ItemStack getItemAsSetup(FurnCraftChestEgg fccEgg, ItemStack item) {
        if (fccEgg.getSetupCost() != null && fccEgg.getSetupCost().getAmount(item) != 0) {
            return getItemSetupStack(fccEgg, item);
        }
        return null;
    }

    private ItemStack getItemAsInput(FurnCraftChestEgg fccEgg, InputRecipe recipe, ItemStack item) {
        if (contains(recipe.getInput(), item)) {
            return getItemRecipeStack(fccEgg, recipe, item);
        }
        if (recipe instanceof VariantRecipe vr) {
            if (vr.acceptsInputMaterial(item.getType())) {
                return getItemRecipeStack(fccEgg, recipe, item);
            }
        }
        return null;
    }

    private ItemStack getItemAsOutput(FurnCraftChestEgg fccEgg, InputRecipe recipe, ItemStack item) {
        if (recipe instanceof ProductionRecipe) {
            ProductionRecipe output = (ProductionRecipe) recipe;
            if (contains(output.getOutput(), item)) {
                return getItemRecipeStack(fccEgg, recipe, item);
            }
        }
        if (recipe instanceof RandomOutputRecipe random) {
            for (Map.Entry<ItemMap, Double> entry : random.getOutputs().entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0 && contains(entry.getKey(), item)) {
                    return getItemRecipeStack(fccEgg, recipe, item);
                }
            }
        }
        if (recipe instanceof CompactingRecipe) {
            CompactingRecipe output = (CompactingRecipe) recipe;
            if (String.join("", ItemUtils.getLore(item)).equals(output.getCompactedLore())) {
                return getItemRecipeStack(fccEgg, recipe, item);
            }
        }
        if (recipe instanceof VariantRecipe vr) {
            if (vr.producesOutputMaterial(item.getType())) {
                return getItemRecipeStack(fccEgg, recipe, item);
            }
        }
        return null;
    }

    /** Whether the map holds the item, regardless of its enchantments, durability or anvil cost */
    private static boolean contains(ItemMap map, ItemStack item) {
        if (map.getAmount(item) != 0) {
            return true;
        }
        ItemStack stripped = stripVariance(item);
        for (Map.Entry<ItemStack, Integer> entry : map.getAllItems().entrySet()) {
            if (entry.getValue() > 0 && stripVariance(entry.getKey()).isSimilar(stripped)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack stripVariance(ItemStack item) {
        ItemStack stripped = item.asOne();
        for (DataComponentType type : IGNORED_COMPONENTS) {
            stripped.unsetData(type);
        }
        return stripped;
    }

    private ItemStack getItemRecipeStack(FurnCraftChestEgg fccEgg, InputRecipe recipe, ItemStack item) {
        ItemStack is = new ItemStack(recipe.getRecipeRepresentationMaterial());
        FMText.name(is, FMText.factory(fccEgg.getName()));
        List<Component> lore = new ArrayList<>();
        lore.add(FMText.recipe(recipe.getName()));
        lore.add(Component.empty());
        lore.add(FMText.section("Input"));
        for (String input : recipe.getTextualInputRepresentation(null, null)) {
            lore.add(formatIngredient(input, recipe.formatLine(input), item));
        }
        lore.add(FMText.section("Output"));
        for (String output : recipe.getTextualOutputRepresentation(null, null)) {
            lore.add(formatIngredient(output, recipe.formatLine(output), item));
        }
        FMText.lore(is, lore.toArray(new Component[0]));
        return is;
    }

    private ItemStack getItemSetupStack(FurnCraftChestEgg fccEgg, ItemStack item) {
        ItemStack is = new ItemStack(Material.CRAFTING_TABLE);
        FMText.name(is, FMText.factory(fccEgg.getName()));
        List<Component> lore = new ArrayList<>();
        lore.add(FMText.section("Creation cost"));
        for (Map.Entry<ItemStack, Integer> entry : fccEgg.getSetupCost().getAllItems().entrySet()) {
            String recipeRepresentation = entry.getValue() + " " + InputRecipe.formatIngredientName(entry.getKey());
            Component line = Component.text().append(FMText.icon(entry.getKey()))
                .append(FMText.legacy(recipeRepresentation).colorIfAbsent(CivText.VALUE)).build();
            lore.add(formatIngredient(recipeRepresentation, line, item));
        }
        FMText.lore(is, lore.toArray(new Component[0]));
        return is;
    }

    /** The ingredient that was looked up is bold, so it stands out from the rest of the recipe */
    private Component formatIngredient(String recipeRepresentation, Component ingredient, ItemStack is) {
        if (recipeRepresentation.matches("\\d+ " + Pattern.quote(InputRecipe.formatIngredientName(is)))) {
            ingredient = ingredient.decoration(TextDecoration.BOLD, true);
        }
        return FMText.entry(ingredient);
    }
}
