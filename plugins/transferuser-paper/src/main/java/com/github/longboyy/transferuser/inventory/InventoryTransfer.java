package com.github.longboyy.transferuser.inventory;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.InvseeAPI;
import com.janboerman.invsee.spigot.api.MainSpectatorInventory;
import com.janboerman.invsee.spigot.api.response.SaveResponse;
import com.janboerman.invsee.spigot.api.response.SpectateResponse;
import com.janboerman.invsee.spigot.api.template.PlayerInventorySlot;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Moves a player's inventory (storage, armour and offhand) to another player
 * using InvSee++, which handles both online and offline players.
 */
public final class InventoryTransfer {

    private final NamespacedKey BOUND_ARMOUR_KEY;

    private final InvseeAPI api;
    private final CreationOptions<PlayerInventorySlot> options;

    public InventoryTransfer(Plugin plugin, InvseeAPI api) {
        this.BOUND_ARMOUR_KEY = new NamespacedKey(SimpleAdminHacks.instance(), "SAH_ArmourBound");
        this.api = api;
        this.options = api.mainInventoryCreationOptions()
            .withPlugin(plugin)
            .withOfflinePlayerSupport(true)
            .withUnknownPlayerSupport(false)
            .withBypassExemptedPlayers(true);
    }

    /**
     * Overwrites the new player's inventory with the old player's, then wipes the old player's inventory.
     * Completes on the main thread with the number of item stacks moved, or exceptionally with an
     * {@link InventoryTransferException}.
     */
    public CompletableFuture<Integer> transfer(OfflinePlayer oldPlayer, OfflinePlayer newPlayer) {
        CompletableFuture<MainSpectatorInventory> oldFuture = load(oldPlayer);
        CompletableFuture<MainSpectatorInventory> newFuture = load(newPlayer);

        return CompletableFuture.allOf(oldFuture, newFuture)
            .thenComposeAsync(ignored -> {
                MainSpectatorInventory oldInventory = oldFuture.join();
                MainSpectatorInventory newInventory = newFuture.join();
                int itemCount = countItems(oldInventory);

                newInventory.setStorageContents(unbind(oldInventory.getStorageContents(), oldPlayer.getUniqueId()));
                newInventory.setArmourContents(unbind(oldInventory.getArmourContents(), oldPlayer.getUniqueId()));
                newInventory.setOffHandContents(unbind(oldInventory.getOffHandContents(), oldPlayer.getUniqueId()));

                // Only wipe the old player once the new player's inventory is saved, so a failed save never loses items
                return save(newInventory)
                    .thenComposeAsync(saved -> {
                        clear(oldInventory);
                        return save(oldInventory).exceptionally(e -> {
                            throw new CompletionException(new InventoryTransferException("Items were copied to "
                                + newPlayer.getName() + " but " + oldPlayer.getName()
                                + "'s inventory could not be wiped, items are duplicated", unwrap(e)));
                        });
                    }, this.api.serverThreadExecutor)
                    .thenApplyAsync(saved -> itemCount, this.api.serverThreadExecutor);
            }, this.api.serverThreadExecutor);
    }

    private CompletableFuture<MainSpectatorInventory> load(OfflinePlayer player) {
        CompletableFuture<SpectateResponse<MainSpectatorInventory>> future =
            this.api.mainSpectatorInventory(player.getUniqueId(), nameOf(player), this.options);
        return future.thenApply(response -> {
            if (!response.isSuccess()) {
                throw new CompletionException(new InventoryTransferException(
                    "Could not load the inventory of " + nameOf(player) + ": " + response.getReason()));
            }
            return response.getInventory();
        });
    }

    private CompletableFuture<Void> save(MainSpectatorInventory inventory) {
        return this.api.saveInventory(inventory).thenAccept(response -> {
            if (!response.isSaved()) {
                throw new CompletionException(new InventoryTransferException(
                    "InvSee++ failed to save the inventory of " + inventory.getSpectatedPlayerName()));
            }
        });
    }

    /**
     * Copies the items, removing the ArmourBound binding from armour bound to the old player so the new player can
     * wear it. Armour bound to anyone else stays bound. Armour can be in any slot, so every item is checked.
     */
    private ItemStack[] unbind(@Nullable ItemStack[] items, UUID oldPlayer) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.isEmpty()) {
                continue;
            }
            copy[i] = item.clone();
            String boundTo = copy[i].getPersistentDataContainer().get(BOUND_ARMOUR_KEY, PersistentDataType.STRING);
            if (oldPlayer.toString().equalsIgnoreCase(boundTo)) {
                copy[i].editPersistentDataContainer(pdc -> pdc.remove(BOUND_ARMOUR_KEY));
            }
        }
        return copy;
    }

    private static void clear(MainSpectatorInventory inventory) {
        inventory.setStorageContents(new ItemStack[inventory.getStorageContents().length]);
        inventory.setArmourContents(new ItemStack[inventory.getArmourContents().length]);
        inventory.setOffHandContents(new ItemStack[inventory.getOffHandContents().length]);
    }

    private static int countItems(MainSpectatorInventory inventory) {
        return count(inventory.getStorageContents())
            + count(inventory.getArmourContents())
            + count(inventory.getOffHandContents());
    }

    private static int count(@Nullable ItemStack[] items) {
        return (int) Arrays.stream(items).filter(item -> item != null && !item.isEmpty()).count();
    }

    private static String nameOf(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    public static Throwable unwrap(Throwable throwable) {
        while (throwable instanceof CompletionException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }
}
