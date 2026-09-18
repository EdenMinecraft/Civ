package com.untamedears.itemexchange.rules;

import com.untamedears.itemexchange.ItemExchangeConfig;
import com.untamedears.itemexchange.rules.ExchangeRule.Type;
import com.untamedears.itemexchange.utility.Utilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import vg.civcraft.mc.civmodcore.inventory.InventoryUtils;
import vg.civcraft.mc.civmodcore.utilities.Validation;
import vg.civcraft.mc.civmodcore.world.WorldUtils;

/**
 * This class represents a specific trade within a shop, a set of inputs and outputs, or a donation.
 */
public final class TradeRule implements Validation {

    private final List<ExchangeRule> inputs = new ArrayList<>();

    private final List<ExchangeRule> outputs = new ArrayList<>();

    private Inventory inventory;

    public TradeRule(Inventory inventory) {
        setInventory(inventory);
    }

    @Override
    public boolean isValid() {
        if (CollectionUtils.isEmpty(this.inputs)) {
            return false;
        }
        if (isOversized()) {
            return false;
        }
        for (ExchangeRule input : this.inputs) {
            if (input == null || input.isBroken() || input.getType() != Type.INPUT) {
                return false;
            }
        }
        for (ExchangeRule output : this.outputs) {
            if (output == null || output.isBroken() || output.getType() != Type.OUTPUT) {
                return false;
            }
        }
        if (!InventoryUtils.isValidInventory(this.inventory)) {
            return false;
        }
        if (!WorldUtils.isValidLocation(this.inventory.getLocation())) {
            return false;
        }
        return true;
    }

    /**
     * Checks whether this trade has more inputs or outputs than the server allows. An oversized trade is rejected
     * outright rather than trimmed: dropping an input would let the buyer underpay, and dropping an output would
     * shortchange them.
     *
     * @return Returns true if this trade exceeds the configured limits.
     */
    public boolean isOversized() {
        final int maxInputs = ItemExchangeConfig.getMaxTradeInputs();
        if (maxInputs > 0 && this.inputs.size() > maxInputs) {
            return true;
        }
        final int maxOutputs = ItemExchangeConfig.getMaxTradeOutputs();
        if (maxOutputs > 0 && this.outputs.size() > maxOutputs) {
            return true;
        }
        return false;
    }

    /**
     * Determines how many times this rule matches with the given inventory.
     *
     * @return The number of trades that can be performed.
     */
    public int calculateStock() {
        return Utilities.calculateStock(this.outputs, this.inventory);
    }

    /**
     * Gets the trade's input rules.
     *
     * @return The trade's input rules, of which there will always be at least one for a valid trade.
     */
    public List<ExchangeRule> getInputs() {
        return Collections.unmodifiableList(this.inputs);
    }

    /**
     * Adds an input rule to the trade.
     *
     * @param input The input rule to add.
     */
    public void addInput(ExchangeRule input) {
        this.inputs.add(input);
    }

    /**
     * Gets the trade's first input rule.
     *
     * @return The trade's first input rule, or null if it has none.
     * @apiNote Prefer {@link TradeRule#getInputs()}, as a trade may have several inputs.
     */
    public ExchangeRule getInput() {
        return this.inputs.isEmpty() ? null : this.inputs.get(0);
    }

    /**
     * Sets the trade's inputs to the single given rule.
     *
     * @param input The input rule to set.
     */
    public void setInput(ExchangeRule input) {
        this.inputs.clear();
        this.inputs.add(input);
    }

    /**
     * Checks whether the trade has any outputs, which is to say whether it's an exchange rather than a donation.
     *
     * @return Returns true if the trade has at least one output rule.
     */
    public boolean hasOutputs() {
        return !this.outputs.isEmpty();
    }

    /**
     * @return Returns true if the trade has at least one output rule.
     * @apiNote Prefer {@link TradeRule#hasOutputs()}.
     */
    public boolean hasOutput() {
        return hasOutputs();
    }

    /**
     * Gets the trade's output rules.
     *
     * @return The trade's output rules, which will be empty if the trade is a donation.
     */
    public List<ExchangeRule> getOutputs() {
        return Collections.unmodifiableList(this.outputs);
    }

    /**
     * Adds an output rule to the trade.
     *
     * @param output The output rule to add.
     */
    public void addOutput(ExchangeRule output) {
        this.outputs.add(output);
    }

    /**
     * Gets the trade's first output rule.
     *
     * @return The trade's first output rule, or null if it has none.
     * @apiNote Prefer {@link TradeRule#getOutputs()}, as a trade may have several outputs.
     */
    public ExchangeRule getOutput() {
        return this.outputs.isEmpty() ? null : this.outputs.get(0);
    }

    /**
     * Sets the trade's outputs to the single given rule.
     *
     * @param output The output rule to set.
     */
    public void setOutput(ExchangeRule output) {
        this.outputs.clear();
        this.outputs.add(output);
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Block getBlock() {
        return Objects.requireNonNull(this.inventory.getLocation()).getBlock();
    }

}
