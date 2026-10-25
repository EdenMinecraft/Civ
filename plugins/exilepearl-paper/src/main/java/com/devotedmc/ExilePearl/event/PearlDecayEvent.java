package com.devotedmc.ExilePearl.event;

import com.devotedmc.ExilePearl.ExilePearl;
import com.google.common.base.Preconditions;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event that is called whenever a single pearl is devayed
 * <p>
 * Pearls are decayed all at once on a regular schedule and whenever that
 * happens, this event will be called for each of them
 *
 * @author Gordon
 */
public class PearlDecayEvent extends Event implements Cancellable {

    // Handler list for spigot events
    private static final HandlerList handlers = new HandlerList();

    private boolean cancelled;
    private ExilePearl pearl;
    private int amount;
    private final boolean preview;

    /**
     * Creates a new PearlDecayEvent instance.
     *
     * @param pearl  Pearl to decay
     * @param amount Health amount the pearl health is reduced by
     */
    public PearlDecayEvent(ExilePearl pearl, int amount) {
        this(pearl, amount, false);
    }

    /**
     * Creates a new PearlDecayEvent instance.
     *
     * @param pearl   Pearl to decay
     * @param amount  Health amount the pearl health is reduced by
     * @param preview true when fired only to compute the effective decay amount (e.g. for lore),
     *                without any health actually being removed. Listeners that apply side effects
     *                (logging, metrics) should skip preview events; modifiers may still adjust the amount.
     */
    public PearlDecayEvent(ExilePearl pearl, int amount, boolean preview) {
        Preconditions.checkNotNull(pearl, "pearl");
        this.pearl = pearl;
        this.amount = amount;
        this.preview = preview;
    }

    /**
     * @return true when this event is a dry-run to compute the effective decay amount, not a real decay
     */
    public boolean isPreview() {
        return preview;
    }

    /**
     * Gets the pearl being decayed
     *
     * @return The pearl decaying
     */
    public ExilePearl getPearl() {
        return pearl;
    }

    /**
     * @return Amount of damage that will be dealt to the pearl
     */
    public int getDamageAmount() {
        return amount;
    }

    /**
     * Sets the amount of damage to deal to the pearl
     *
     * @param amount Amount of damage to deal
     */
    public void setDamageAmount(int amount) {
        this.amount = amount;
    }

    /**
     * Gets whether the event is cancelled
     *
     * @return true if the event is cancelled
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets whether the event is cancelled
     *
     * @param cancelled whether the event is cancelled
     */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
