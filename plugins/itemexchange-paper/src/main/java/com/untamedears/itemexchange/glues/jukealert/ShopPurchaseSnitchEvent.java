package com.untamedears.itemexchange.glues.jukealert;

import com.untamedears.itemexchange.events.SuccessfulPurchaseEvent;
import com.untamedears.itemexchange.rules.TradeRule;
import com.untamedears.jukealert.model.Snitch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public class ShopPurchaseSnitchEvent extends SuccessfulPurchaseEvent {

    private static final HandlerList handlers = new HandlerList();

    private final Snitch snitch;

    protected ShopPurchaseSnitchEvent(Snitch snitch, Player player, TradeRule trade, ItemStack[] input, ItemStack[] output) {
        super(player, trade, input, output);
        this.snitch = snitch;
    }

    public Snitch getSnitch() {
        return this.snitch;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public static ShopPurchaseSnitchEvent emit(Snitch snitch, Player player, TradeRule trade, ItemStack[] input, ItemStack[] output) {
        ShopPurchaseSnitchEvent event = new ShopPurchaseSnitchEvent(snitch, player, trade, input, output);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }
}
