package com.untamedears.itemexchange.utility;

import co.aikar.commands.InvalidCommandArgument;
import com.google.common.base.Preconditions;
import com.untamedears.itemexchange.ItemExchangeConfig;
import com.untamedears.itemexchange.ItemExchangePlugin;
import com.untamedears.itemexchange.rules.BulkExchangeRule;
import com.untamedears.itemexchange.rules.ExchangeRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Switch;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import vg.civcraft.mc.civmodcore.inventory.InventoryUtils;
import vg.civcraft.mc.civmodcore.inventory.items.ItemUtils;
import vg.civcraft.mc.civmodcore.utilities.KeyedUtils;
import vg.civcraft.mc.civmodcore.utilities.NullUtils;
import vg.civcraft.mc.civmodcore.world.WorldUtils;

/**
 * A series of Utilities of ItemExchange
 */
public final class Utilities {

    /**
     * Tests whether a given item is an exchange rule or bulk exchange rule.
     *
     * @param item The item to test.
     * @return Returns true if the item is an exchange rule or bulk exchange rule.
     */
    public static boolean isExchangeRule(final ItemStack item) {
        if (item == null) {
            return false;
        }
        return ExchangeRule.fromItem(item) != null
            || BulkExchangeRule.fromItem(item) != null;
    }

    /**
     * Attempts to give a player an exchange rule.
     *
     * @param player The player to give the exchange rule to.
     * @param rule   The exchange rule to give the player.
     */
    public static void givePlayerExchangeRule(final Player player, final ExchangeRule rule) {
        RuntimeException error = new InvalidCommandArgument("Could not create that rule.");
        if (player == null || rule == null) {
            throw error;
        }
        if (!InventoryUtils.safelyAddItemsToInventory(player.getInventory(), new ItemStack[]{rule.toItem()})) {
            throw error;
        }
    }

    /**
     * Gives items to an inventory or drops them at that inventory's location.
     *
     * @param inventory The inventory to give the items to. It must have a location, like a chest inventory.
     * @param items     The items to give to the inventory.
     */
    public static void giveItemsOrDrop(@NotNull final Inventory inventory, @NotNull final ItemStack... items) {
        Preconditions.checkArgument(InventoryUtils.isValidInventory(inventory));
        Preconditions.checkArgument(WorldUtils.isValidLocation(inventory.getLocation()));
        Preconditions.checkArgument(ArrayUtils.isNotEmpty(items));
        World world = inventory.getLocation().getWorld();
        assert world != null;
        for (Map.Entry<Integer, ItemStack> entry : inventory.addItem(items).entrySet()) {
            world.dropItem(inventory.getLocation(), entry.getValue());
        }
    }

    /**
     * Checks whether a series of enchantments matches [loosely] another series of enchantments.
     *
     * @param ruleEnchants          The enchantments that MUST exist.
     * @param metaEnchants          The enchantments that exist.
     * @param allowUnlistedEnchants Is metaEnchants allowed to include enchantments not included in ruleEnchants?
     * @return Returns true if the meta enchantments satisfy the rule enchantments.
     */
    public static boolean conformsRequiresEnchants(final Map<Enchantment, Integer> ruleEnchants,
                                                   final Map<Enchantment, Integer> metaEnchants,
                                                   final boolean allowUnlistedEnchants) {
        if (MapUtils.isEmpty(ruleEnchants)) {
            return allowUnlistedEnchants || MapUtils.isEmpty(metaEnchants);
        }
        if (MapUtils.isEmpty(metaEnchants) || metaEnchants.size() < ruleEnchants.size()) {
            return false;
        }
        if (!allowUnlistedEnchants && metaEnchants.size() != ruleEnchants.size()) {
            return false;
        }
        for (final var ruleEnchant : ruleEnchants.entrySet()) {
            if (!metaEnchants.containsKey(ruleEnchant.getKey())) {
                return false;
            }
            if (ruleEnchant.getValue() != ExchangeRule.ANY) {
                if (!NullUtils.equalsNotNull(metaEnchants.get(ruleEnchant.getKey()), ruleEnchant.getValue())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Trigger the successful transaction buttons.
     *
     * @param shop The block representing the shop.
     */
    public static void successfulTransactionButton(Block shop) {
        Stream.of(shop, WorldUtils.getOtherDoubleChestBlock(shop, true))
            .filter(WorldUtils::isValidBlock)
            .filter((block) -> ItemExchangeConfig.hasSuccessButtonBlock(block.getType()))
            .distinct()
            .forEach((block) -> {
                if (!(block.getBlockData() instanceof Directional directional)) {
                    return;
                }
                final BlockFace backFace = directional.getFacing().getOppositeFace();
                final Block behindBlock = block.getRelative(backFace);
                if (!WorldUtils.isValidBlock(behindBlock) || !behindBlock.getType().isOccluding()) {
                    return;
                }
                for (BlockFace face : WorldUtils.ALL_SIDES) {
                    if (face.getOppositeFace() == backFace) {
                        continue;
                    }
                    final Block buttonBlock = behindBlock.getRelative(face);
                    if (!WorldUtils.isValidBlock(buttonBlock) || !Tag.BUTTONS.isTagged(buttonBlock.getType())) {
                        continue;
                    }
                    if (!(buttonBlock.getBlockData() instanceof Switch button)) {
                        continue;
                    }
                    if (WorldUtils.getAttachedFace(button) != face.getOppositeFace()) {
                        continue;
                    }
                    button.setPowered(true);
                    buttonBlock.setBlockData(button);
                    // Wait to de-power the block
                    Bukkit.getScheduler().scheduleSyncDelayedTask(ItemExchangePlugin.getInstance(), () -> {
                        final Block newBlock = buttonBlock.getLocation().getBlock(); // Refresh block
                        if (!(newBlock.getBlockData() instanceof Switch newButton)
                            || !button.matches(newButton)) {
                            return;
                        }
                        newButton.setPowered(false);
                        newBlock.setBlockData(newButton);
                    }, 30L);
                }
            });
    }

    // ------------------------------------------------------------
    // Stock
    // ------------------------------------------------------------

    /**
     * A snapshot of which of an inventory's items each of a set of rules will accept. Conformance is worked out once
     * up front because testing it is not cheap, which then allows the reservation below to be run repeatedly for next
     * to nothing.
     */
    private record StockSnapshot(ItemStack[] contents, int[] amounts, List<ExchangeRule> rules,
                                 boolean[][] conformance) {

        private static StockSnapshot of(final Collection<ExchangeRule> rules, final Inventory inventory) {
            if (CollectionUtils.isEmpty(rules) || !InventoryUtils.isValidInventory(inventory)) {
                return null;
            }
            final ItemStack[] contents = inventory.getStorageContents();
            final int[] amounts = new int[contents.length];
            for (int slot = 0; slot < contents.length; slot++) {
                final ItemStack item = contents[slot];
                amounts[slot] = !ItemUtils.isValidItem(item) || isExchangeRule(item) ? 0 : item.getAmount();
            }
            final var ruleList = new ArrayList<>(rules);
            final var conformance = new boolean[ruleList.size()][contents.length];
            for (int index = 0; index < ruleList.size(); index++) {
                final ExchangeRule rule = ruleList.get(index);
                if (rule == null) {
                    return null;
                }
                for (int slot = 0; slot < contents.length; slot++) {
                    conformance[index][slot] = amounts[slot] > 0 && rule.conforms(contents[slot]);
                }
            }
            return new StockSnapshot(contents, amounts, ruleList, conformance);
        }

        /**
         * Greedily reserves the items needed to satisfy every rule a set number of times over. Rules are served in
         * order and no item is ever claimed twice, so two rules that happen to match the same items will each be
         * given their own share rather than both laying claim to the same stack.
         *
         * @param multiplier How many times over the rules must be satisfied.
         * @param claimed    Collects the reserved items, or null to only test satisfiability.
         * @return Returns true if every rule was fully satisfied.
         */
        private boolean reserve(final int multiplier, final Collection<ItemStack> claimed) {
            if (multiplier < 1) {
                return false;
            }
            // Tracks how much of each slot is still up for grabs.
            final int[] remaining = this.amounts.clone();
            for (int index = 0; index < this.rules.size(); index++) {
                long required = (long) this.rules.get(index).getAmount() * multiplier;
                final boolean[] conforms = this.conformance[index];
                for (int slot = 0; slot < remaining.length && required > 0L; slot++) {
                    if (remaining[slot] <= 0 || !conforms[slot]) {
                        continue;
                    }
                    final int taken = (int) Math.min(remaining[slot], required);
                    remaining[slot] -= taken;
                    required -= taken;
                    if (claimed != null) {
                        final ItemStack clone = this.contents[slot].clone();
                        clone.setAmount(taken);
                        claimed.add(clone);
                    }
                }
                if (required > 0L) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Determines the stock items themselves from a given inventory, which can then be used for trade purposes.
     *
     * @param rules     The rules that must all be satisfied.
     * @param inventory The inventory to determine the stock within.
     * @return An array of cloned items that can then be used within trade APIs, which will be empty if the given
     * inventory cannot satisfy every rule at once.
     */
    public static ItemStack[] getStock(final Collection<ExchangeRule> rules, final Inventory inventory) {
        final StockSnapshot snapshot = StockSnapshot.of(rules, inventory);
        if (snapshot == null) {
            return new ItemStack[0];
        }
        final var claimed = new ArrayList<ItemStack>();
        if (!snapshot.reserve(1, claimed)) {
            return new ItemStack[0];
        }
        return claimed.toArray(new ItemStack[0]);
    }

    /**
     * Determines how many times the given rules can all be satisfied by the given inventory at once.
     *
     * @param rules     The rules that must all be satisfied.
     * @param inventory The inventory to determine the stock within.
     * @return Returns the number of times the rules can be satisfied.
     */
    public static int calculateStock(final Collection<ExchangeRule> rules, final Inventory inventory) {
        final StockSnapshot snapshot = StockSnapshot.of(rules, inventory);
        if (snapshot == null || !snapshot.reserve(1, null)) {
            return 0;
        }
        // Double until the rules can no longer be satisfied, then binary search the boundary.
        int satisfiable = 1;
        int excessive = 2;
        while (snapshot.reserve(excessive, null)) {
            satisfiable = excessive;
            if (excessive > Integer.MAX_VALUE / 2) {
                return satisfiable;
            }
            excessive *= 2;
        }
        while (excessive - satisfiable > 1) {
            final int middle = satisfiable + ((excessive - satisfiable) / 2);
            if (snapshot.reserve(middle, null)) {
                satisfiable = middle;
            } else {
                excessive = middle;
            }
        }
        return satisfiable;
    }

    // ------------------------------------------------------------
    // Stringifiers
    // ------------------------------------------------------------

    public static String leveledEnchantsToString(final Map<Enchantment, Integer> leveledEnchants) {
        if (MapUtils.isEmpty(leveledEnchants)) {
            return "[]";
        }
        return "[" +
            leveledEnchants.entrySet().stream()
                .map(entry -> KeyedUtils.getString(entry.getKey()) + ":" + entry.getValue())
                .collect(Collectors.joining(",")) +
            "]";
    }

    public static String enchantsToString(final Collection<Enchantment> enchants) {
        if (CollectionUtils.isEmpty(enchants)) {
            return "[]";
        }
        return "[" +
            enchants.stream()
                .map(entry -> KeyedUtils.getString(entry.getKey()))
                .collect(Collectors.joining(",")) +
            "]";
    }

    public static String potionDataToString(final PotionType data) {
        if (data == null) {
            return null;
        }
        return "PotionData{" +
            "type=" + data.name() + "," +
            "}";
    }

    public static String potionEffectsToString(final Collection<PotionEffect> effects) {
        if (CollectionUtils.isEmpty(effects)) {
            return "[]";
        }
        return "[" +
            effects.stream()
                .map(entry -> "PotionEffect{" + entry.serialize() + "}")
                .collect(Collectors.joining(",")) +
            "]";
    }

}
