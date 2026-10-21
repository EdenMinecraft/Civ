package com.untamedears.itemexchange.rules;

import static com.untamedears.itemexchange.rules.ExchangeRule.Type;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.untamedears.itemexchange.ItemExchangeConfig;
import com.untamedears.itemexchange.ItemExchangePlugin;
import com.untamedears.itemexchange.events.BlockInventoryRequestEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import vg.civcraft.mc.civmodcore.inventory.InventoryUtils;
import vg.civcraft.mc.civmodcore.utilities.Validation;
import vg.civcraft.mc.civmodcore.world.WorldUtils;

/**
 * Class that represents an entire shop.
 */
public final class ShopRule implements Validation {

    private final ItemExchangePlugin PLUGIN = ItemExchangePlugin.getInstance();

    private final List<TradeRule> trades = new ArrayList<>();

    private int currentTradeIndex;

    private int oversizedTrades;

    @Override
    public boolean isValid() {
        if (CollectionUtils.isEmpty(this.trades)) {
            return false;
        }
        return true;
    }

    public List<TradeRule> getTrades() {
        return this.trades;
    }

    public int getCurrentTradeIndex() {
        return this.currentTradeIndex;
    }

    public void setCurrentTradeIndex(int currentTrade) {
        this.currentTradeIndex = currentTrade;
    }

    public TradeRule getCurrentTrade() {
        if (this.trades.isEmpty()) {
            return null;
        }
        if (this.currentTradeIndex < 0) {
            return null;
        }
        if (this.currentTradeIndex >= this.trades.size()) {
            return null;
        }
        return this.trades.get(this.currentTradeIndex);
    }

    /**
     * @return Returns true if any of this shop's exchanges were disabled for exceeding the configured size limits.
     */
    public boolean hasOversizedTrades() {
        return this.oversizedTrades > 0;
    }

    /**
     * Tells a player that some of this shop's exchanges have been disabled for being too large. Without this the shop
     * would fail silently: an oversized exchange never enters the catalogue, so there's no gap in the listing to
     * notice, and the rule items in the container still look perfectly normal.
     */
    public void warnAboutOversizedTrades(Player player) {
        if (this.oversizedTrades < 1) {
            return;
        }
        player.sendMessage(ChatColor.RED + (this.oversizedTrades == 1
            ? "1 exchange exceeds this server's size limit and has been disabled."
            : this.oversizedTrades + " exchanges exceed this server's size limit and have been disabled."));
    }

    public TradeRule cycleTrades(boolean forward) {
        if (this.trades.isEmpty()) {
            return null;
        }
        this.currentTradeIndex += forward ? 1 : -1;
        if (this.currentTradeIndex < 0) {
            this.currentTradeIndex = this.trades.size() - 1;
        } else if (this.currentTradeIndex >= this.trades.size()) {
            this.currentTradeIndex = 0;
        }
        return getCurrentTrade();
    }

    public void presentShopToPlayer(Player player) {
        TradeRule trade = getCurrentTrade();
        if (trade == null) {
            throw new NullPointerException("Could not message player about trade... this shouldn't happen.");
        }
        player.sendMessage(String.format("%s(%d/%d) exchanges present.",
            ChatColor.YELLOW, this.currentTradeIndex + 1, this.trades.size()));
        for (ExchangeRule input : trade.getInputs()) {
            for (String line : input.getDisplayInfo()) {
                player.sendMessage(line);
            }
        }
        if (trade.hasOutputs()) {
            for (ExchangeRule output : trade.getOutputs()) {
                for (String line : output.getDisplayInfo()) {
                    player.sendMessage(line);
                }
            }
            PLUGIN.debug("[ShopRule] Calculating stock.");
            int stock = trade.calculateStock();
            player.sendMessage(ChatColor.YELLOW + "" + stock + " exchange" + (stock == 1 ? "" : "s") + " available.");
        }
        warnAboutOversizedTrades(player);
    }

    // ------------------------------------------------------------
    // Shop Resolution
    // ------------------------------------------------------------

    private void resolveInventories(final Block block,
                                    final Set<Inventory> found,
                                    final int remainingRecursion,
                                    final BlockFace cameFrom) {
        if (ItemExchangeConfig.hasCompatibleShopBlock(block.getType())) {
            PLUGIN.debug("[RELAY] Found shop block. (Total: " + found.size() + ")");
            BlockInventoryRequestEvent event = BlockInventoryRequestEvent.emit(block, null,
                BlockInventoryRequestEvent.Purpose.INSPECTION);
            final Inventory inventory = event.getInventory();
            if (inventory != null) {
                found.add(inventory);
            }
            return;
        }
        if (ItemExchangeConfig.hasRelayCompatibleBlock(block.getType())) {
            PLUGIN.debug("[RELAY] Found relay block.");
            int reach = ItemExchangeConfig.getRelayReachDistance();
            if (reach <= 0) {
                PLUGIN.debug("[RELAY] Relay has no reach distance.");
                return;
            }
            if (remainingRecursion < 0) {
                PLUGIN.debug("[RELAY] Relay recursion limit reached.");
                return;
            }
            for (BlockFace face : WorldUtils.ALL_SIDES) {
                if (face.equals(cameFrom)) {
                    continue;
                }
                PLUGIN.debug("[RELAY] Emitting relay ray trace: " + face.name());
                BlockIterator iterator = WorldUtils.getBlockIterator(block.getRelative(face), face, reach);
                while (iterator.hasNext()) {
                    Block current = iterator.next();
                    if (ItemExchangeConfig.hasRelayPermeableBlock(current.getType())) {
                        PLUGIN.debug("[RELAY] Found permeable block.");
                        continue;
                    }
                    if (!ItemExchangeConfig.canBeInteractedWith(current.getType())) {
                        PLUGIN.debug("[RELAY] Ending search.");
                        break;
                    }
                    resolveInventories(current, found, remainingRecursion - 1, face.getOppositeFace());
                    break;
                }
            }
        }
    }

    private List<ExchangeRule> extractRulesFromInventory(Inventory inventory) {
        List<ExchangeRule> found = Lists.newArrayList();
        PLUGIN.debug("[Resolve] Searching inventory [" + inventory.getType().name() + "] for exchange rules");
        for (ItemStack item : inventory.getContents()) {
            ExchangeRule rule = ExchangeRule.fromItem(item);
            if (rule != null) {
                PLUGIN.debug("[Resolve] \tExchange Rule found.");
                found.add(rule);
                continue;
            }
            BulkExchangeRule bulk = BulkExchangeRule.fromItem(item);
            if (bulk != null) {
                PLUGIN.debug("[Resolve] \tBulk Exchange Rule found.");
                found.addAll(bulk.rules());
                //continue;
            }
        }
        return found;
    }

    /**
     * Finishes off a gathered trade, keeping it if it's usable and otherwise noting why it was thrown away.
     */
    private void completeTrade(List<TradeRule> trades, TradeRule trade) {
        if (trade.isValid()) {
            trades.add(trade);
        } else if (trade.isOversized()) {
            this.oversizedTrades++;
            PLUGIN.debug("[Resolve] Discarding oversized trade.");
        }
    }

    /**
     * Groups a shop's rules into trades. A trade is an unbroken run of input rules followed by an unbroken run of
     * output rules, so an input that follows an output begins the next trade. This keeps the classic
     * one-input-to-one-output layout working exactly as it always has, while allowing a trade to ask for several
     * inputs and to pay out several outputs.
     */
    private List<TradeRule> extractTradesFromInventory(Inventory inventory) {
        List<TradeRule> trades = Lists.newArrayList();
        List<ExchangeRule> rules = extractRulesFromInventory(inventory);
        TradeRule currentTrade = new TradeRule(inventory);
        for (ExchangeRule rule : rules) {
            // A broken rule severs whatever trade was being gathered.
            if (rule == null || rule.isBroken()) {
                completeTrade(trades, currentTrade);
                currentTrade = new TradeRule(inventory);
                continue;
            }
            Type type = rule.getType();
            if (type == Type.INPUT) {
                // An input that follows an output starts the next trade.
                if (currentTrade.hasOutputs()) {
                    completeTrade(trades, currentTrade);
                    currentTrade = new TradeRule(inventory);
                }
                currentTrade.addInput(rule);
            } else if (type == Type.OUTPUT) {
                // An output with no preceding input has nothing to attach itself to.
                if (currentTrade.getInputs().isEmpty()) {
                    continue;
                }
                currentTrade.addOutput(rule);
            }
        }
        completeTrade(trades, currentTrade);
        return trades;
    }

    public static ShopRule resolveShop(Block block) {
        if (!WorldUtils.isValidBlock(block)) {
            return null;
        }
        ShopRule shop = new ShopRule();
        Set<Inventory> inventories = Sets.newHashSet();
        shop.resolveInventories(
            block,
            inventories,
            ItemExchangeConfig.getRelayRecursionLimit(),
            BlockFace.SELF);
        inventories.stream()
            .filter(InventoryUtils::isValidInventory)
            .map(shop::extractTradesFromInventory)
            .forEachOrdered(shop.trades::addAll);
        return shop;
    }

}
