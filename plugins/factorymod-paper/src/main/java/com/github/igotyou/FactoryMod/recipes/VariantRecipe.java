package com.github.igotyou.FactoryMod.recipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.github.igotyou.FactoryMod.factories.FurnCraftChestFactory;
import com.github.igotyou.FactoryMod.utility.MultiInventoryWrapper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import vg.civcraft.mc.civmodcore.inventory.items.ItemMap;
import vg.civcraft.mc.civmodcore.inventory.items.ItemUtils;
import vg.civcraft.mc.civmodcore.utilities.TextUtil;
import vg.civcraft.mc.civmodcore.ui.CivText;
import com.github.igotyou.FactoryMod.utility.FMText;

/**
 * A recipe where a base input material is mapped to a base output material,
 * and additional variants are derived by replacing the prefix in the material name.
 * At runtime, the actual variant is selected based on which input material is present
 * in the factory chest.
 */
public class VariantRecipe extends InputRecipe {

    private final ItemStack baseVariantInput;
    private final ItemStack baseVariantOutput;
    private final ItemMap fixedInput;
    private final ItemMap fixedOutput;
    private final Map<Material, Material> inputToOutputMap;
    private final String genericInputName;
    private final String genericOutputName;
    private final String variantPrefix;

    public VariantRecipe(
        String identifier,
        String name,
        int productionTime,
        ItemMap fixedInput,
        ItemStack baseVariantInput,
        ItemMap fixedOutput,
        ItemStack baseVariantOutput,
        Map<Material, Material> inputToOutputMap,
        String variantPrefix
    ) {
        super(identifier, name, productionTime, fixedInput);
        this.fixedInput = fixedInput;
        this.baseVariantInput = baseVariantInput;
        this.fixedOutput = fixedOutput;
        this.baseVariantOutput = baseVariantOutput;
        this.inputToOutputMap = new LinkedHashMap<>(inputToOutputMap);
        this.variantPrefix = variantPrefix;
        this.genericInputName = "Any " + stripVariantPrefix(baseVariantInput);
        this.genericOutputName = "Any " + stripVariantPrefix(baseVariantOutput);
    }

    /**
     * Removes the variant prefix words (e.g. "Oak" for OAK, "Dark Oak" for DARK_OAK) from the item name, so
     * "Stripped Oak Log" becomes "Stripped Log". Falls back to dropping the first word if the prefix is not found.
     */
    private String stripVariantPrefix(ItemStack item) {
        String name = ItemUtils.getItemName(item);
        String prefixWords = variantPrefix.replace('_', ' ');
        String lowerName = name.toLowerCase();
        String lowerPrefix = prefixWords.toLowerCase();
        int index = lowerName.indexOf(lowerPrefix);
        while (index != -1) {
            int end = index + lowerPrefix.length();
            boolean startOk = index == 0 || name.charAt(index - 1) == ' ';
            boolean endOk = end == name.length() || name.charAt(end) == ' ';
            if (startOk && endOk) {
                return (name.substring(0, index) + name.substring(end)).trim().replaceAll(" {2,}", " ");
            }
            index = lowerName.indexOf(lowerPrefix, index + 1);
        }
        int firstSpace = name.indexOf(' ');
        return firstSpace != -1 ? name.substring(firstSpace + 1) : name;
    }

    private String computeMatchingName(ItemStack item) {
        return "Matching " + stripVariantPrefix(item);
    }

    public boolean acceptsInputMaterial(Material material) {
        return inputToOutputMap.containsKey(material);
    }

    public boolean producesOutputMaterial(Material material) {
        return inputToOutputMap.containsValue(material);
    }

    @Override
    public boolean enoughMaterialAvailable(Inventory inputInv) {
        if (!fixedInput.isContainedIn(inputInv)) {
            return false;
        }

        ItemMap invMap = new ItemMap(inputInv);

        for (Map.Entry<Material, Material> entry : inputToOutputMap.entrySet()) {
            ItemStack check = new ItemStack(entry.getKey(), baseVariantInput.getAmount());

            if (invMap.getAmount(check) >= baseVariantInput.getAmount()) {
                return true;
            }

        }

        return false;
    }

    @Override
    public boolean applyEffect(Inventory inputInv, Inventory outputInv, FurnCraftChestFactory fccf) {
        MultiInventoryWrapper combo = new MultiInventoryWrapper(inputInv, outputInv);
        logBeforeRecipeRun(combo, fccf);

        Material selectedInput = null;
        Material selectedOutput = null;
        ItemMap invMap = new ItemMap(inputInv);

        for (Map.Entry<Material, Material> entry : inputToOutputMap.entrySet()) {
            ItemStack check = new ItemStack(entry.getKey(), baseVariantInput.getAmount());

            if (invMap.getAmount(check) >= baseVariantInput.getAmount()) {
                selectedInput = entry.getKey();
                selectedOutput = entry.getValue();
                break;
            }
        }

        if (selectedInput == null) {
            return false;
        }

        ItemMap toRemove = fixedInput.clone();
        toRemove.addItemAmount(new ItemStack(selectedInput, 1), baseVariantInput.getAmount());

        ItemMap toAdd = fixedOutput.clone();
        toAdd.addItemAmount(new ItemStack(selectedOutput, 1), baseVariantOutput.getAmount());

        if (!toRemove.isContainedIn(inputInv)) {
            return false;
        }

        if (!canFitInOutput(toAdd, outputInv)) {
            return false;
        }

        List<ItemStack> insertedOutput = new ArrayList<>();
        if (toRemove.removeSafelyFrom(inputInv)) {
            if (addOutputToInventorySafely(toAdd, outputInv, insertedOutput)) {
                logAfterRecipeRun(combo, fccf);
                return true;
            } else {
                rollbackOutput(outputInv, insertedOutput);
                restoreInput(toRemove, inputInv, fccf);
                return false;
            }
        }

        return false;
    }

    @Override
    public EffectFeasibility evaluateEffectFeasibility(Inventory inputInv, Inventory outputInv) {
        Material selectedOutput = null;
        ItemMap invMap = new ItemMap(inputInv);

        for (Map.Entry<Material, Material> entry : inputToOutputMap.entrySet()) {
            ItemStack check = new ItemStack(entry.getKey(), baseVariantInput.getAmount());

            if (invMap.getAmount(check) >= baseVariantInput.getAmount()) {
                selectedOutput = entry.getValue();
                break;
            }
        }

        if (selectedOutput == null) {
            return new EffectFeasibility(false, "not enough materials");
        }

        ItemMap totalOutput = fixedOutput.clone();
        totalOutput.addItemAmount(new ItemStack(selectedOutput, 1), baseVariantOutput.getAmount());
        boolean isFeasible = totalOutput.fitsIn(outputInv);

        return new EffectFeasibility(isFeasible, isFeasible ? null : "it ran out of storage space");
    }

    @Override
    public List<ItemStack> getInputRepresentation(Inventory i, FurnCraftChestFactory fccf) {
        List<ItemStack> result = new ArrayList<>();

        for (ItemStack is : fixedInput.getItemStackRepresentation()) {
            ItemStack clone = is.clone();

            if (i != null) {
                int possibleRuns = fixedInput.getMultiplesContainedIn(i);
                FMText.lore(clone, FMText.runs(possibleRuns));
            }

            result.add(clone);
        }

        ItemStack variantDisplay = baseVariantInput.clone();
        ItemMeta meta = variantDisplay.getItemMeta();
        meta.displayName(Component.text(genericInputName, CivText.VALUE).decoration(TextDecoration.ITALIC, false));
        variantDisplay.setItemMeta(meta);

        if (i != null) {
            ItemMap invMap = new ItemMap(i);
            int maxVariantRuns = 0;

            for (Material mat : inputToOutputMap.keySet()) {
                int have = invMap.getAmount(new ItemStack(mat, 1));
                maxVariantRuns = Math.max(maxVariantRuns, have / baseVariantInput.getAmount());
            }

            int fixedRuns = fixedInput.getMultiplesContainedIn(i);
            int totalRuns = Math.min(fixedRuns, maxVariantRuns);
            FMText.lore(variantDisplay, FMText.runs(totalRuns));
        }
        result.add(variantDisplay);

        return result;
    }

    @Override
    public List<ItemStack> getOutputRepresentation(Inventory i, FurnCraftChestFactory fccf) {
        List<ItemStack> result = new ArrayList<>();

        for (ItemStack is : fixedOutput.getItemStackRepresentation()) {
            ItemStack clone = is.clone();

            if (i != null) {
                int possibleRuns = calculatePossibleRuns(i);
                FMText.lore(clone, FMText.runs(possibleRuns));
            }

            result.add(clone);
        }

        ItemStack variantDisplay = baseVariantOutput.clone();
        ItemMeta meta = variantDisplay.getItemMeta();
        meta.displayName(Component.text(computeMatchingName(baseVariantOutput), CivText.VALUE).decoration(TextDecoration.ITALIC, false));
        variantDisplay.setItemMeta(meta);

        if (i != null) {
            int possibleRuns = calculatePossibleRuns(i);
            FMText.lore(variantDisplay, FMText.runs(possibleRuns));
        }
        
        result.add(variantDisplay);

        return result;
    }

    @Override
    public List<String> getTextualInputRepresentation(Inventory i, FurnCraftChestFactory fccf) {
        List<String> result = formatLore(fixedInput);
        result.add(baseVariantInput.getAmount() + " " + genericInputName);

        return result;
    }

    @Override
    public List<String> getTextualOutputRepresentation(Inventory i, FurnCraftChestFactory fccf) {
        List<String> result = formatLore(fixedOutput);
        result.add(baseVariantOutput.getAmount() + " " + computeMatchingName(baseVariantOutput));

        return result;
    }

    @Override
    public ItemStack getRecipeRepresentation(Inventory inputInv) {

        Material displayMaterial = baseVariantOutput.getType();
        Material selectedVariantInput = null;

        if (inputInv != null) {
            ItemMap inventoryMap = new ItemMap(inputInv);

            for (Map.Entry<Material, Material> entry : inputToOutputMap.entrySet()) {
                ItemStack check = new ItemStack(entry.getKey(), baseVariantInput.getAmount());

                if (inventoryMap.getAmount(check) >= baseVariantInput.getAmount()) {
                    displayMaterial = entry.getValue();
                    selectedVariantInput = entry.getKey();
                    break;
                }
            }
        }

        ItemStack res = new ItemStack(displayMaterial);
        FMText.name(res, FMText.recipe(getName()));
        List<Component> lore = new ArrayList<>();
        lore.add(FMText.section("Input"));

        ItemMap inventoryMap = inputInv != null ? new ItemMap(inputInv) : null;

        for (Map.Entry<ItemStack, Integer> entry : fixedInput.getAllItems().entrySet()) {

            if (entry.getValue() <= 0) {
                continue;
            }

            Component name = FMText.legacy(formatIngredientName(entry.getKey()));

            if (inputInv != null) {
                int have = inventoryMap.getAmount(entry.getKey());
                lore.add(FMText.entry(FMText.have(have, entry.getValue(), name)));
            } else {
                lore.add(FMText.entry(FMText.amount(entry.getValue(), name)));
            }
        }

        if (inputInv != null) {
            int maxHave = 0;

            if (selectedVariantInput != null) {
                maxHave = inventoryMap.getAmount(new ItemStack(selectedVariantInput, 1));
            } else {
                for (Material mat : inputToOutputMap.keySet()) {
                    maxHave = Math.max(maxHave, inventoryMap.getAmount(new ItemStack(mat, 1)));
                }
            }

            lore.add(FMText.entry(FMText.have(maxHave, baseVariantInput.getAmount(), CivText.value(genericInputName))));
        } else {
            lore.add(FMText.entry(FMText.amount(baseVariantInput.getAmount(), CivText.value(genericInputName))));
        }

        lore.add(Component.empty());
        lore.add(FMText.section("Output"));

        for (String s : formatLore(fixedOutput)) {
            lore.add(FMText.entry(FMText.legacy(s).colorIfAbsent(CivText.VALUE)));
        }

        String variantOutputName;

        if (selectedVariantInput != null) {
            variantOutputName = ItemUtils.getItemName(new ItemStack(displayMaterial));
        } else {
            variantOutputName = computeMatchingName(baseVariantOutput);
        }

        lore.add(FMText.entry(FMText.amount(baseVariantOutput.getAmount(), CivText.value(variantOutputName))));

        lore.add(Component.empty());
        lore.add(Component.text()
            .append(CivText.info("Takes "))
            .append(CivText.value(TextUtil.formatDuration(getProductionTime() * 50, java.util.concurrent.TimeUnit.MILLISECONDS)))
            .build());
        FMText.lore(res, lore.toArray(new Component[0]));

        return res;
    }

    @Override
    public Material getRecipeRepresentationMaterial() {
        return baseVariantOutput.getType();
    }

    @Override
    public String getTypeIdentifier() {
        return "VARIANT";
    }

    private int calculatePossibleRuns(Inventory i) {
        if (i == null) {
            return 0;
        }

        ItemMap invMap = new ItemMap(i);
        int maxVariantRuns = 0;

        for (Material mat : inputToOutputMap.keySet()) {
            int have = invMap.getAmount(new ItemStack(mat, 1));
            maxVariantRuns = Math.max(maxVariantRuns, have / baseVariantInput.getAmount());
        }

        int fixedRuns = fixedInput.getMultiplesContainedIn(i);
        
        return Math.min(fixedRuns, maxVariantRuns);
    }

}
