package com.github.longboyy.transferuser.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import com.github.longboyy.transferuser.TransferUser;
import com.github.longboyy.transferuser.groups.GroupTransfer;
import com.github.longboyy.transferuser.inventory.InventoryTransfer;
import com.github.longboyy.transferuser.inventory.InventoryTransferException;
import java.util.logging.Level;
import net.minelink.ctplus.CombatTagPlus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

public class TransferUserCommand extends BaseCommand {

    private final TransferUser plugin;

    public TransferUserCommand(TransferUser plugin) {
        this.plugin = plugin;
    }

    @CommandAlias("transferuser")
    @Description("Moves a player's NameLayer groups and inventory to another player, wiping the original inventory")
    @CommandPermission("transferuser.op")
    @CommandCompletion("@allplayers @allplayers")
    public void onTransferUser(CommandSender sender, String oldPlayerName, String newPlayerName) {
        OfflinePlayer oldPlayer = Bukkit.getOfflinePlayerIfCached(oldPlayerName);
        if (oldPlayer == null) {
            sender.sendMessage(Component.text("Unknown player: " + oldPlayerName, NamedTextColor.RED));
            return;
        }
        OfflinePlayer newPlayer = Bukkit.getOfflinePlayerIfCached(newPlayerName);
        if (newPlayer == null) {
            sender.sendMessage(Component.text("Unknown player: " + newPlayerName, NamedTextColor.RED));
            return;
        }
        if (oldPlayer.getUniqueId().equals(newPlayer.getUniqueId())) {
            sender.sendMessage(Component.text("Cannot transfer a player to themselves", NamedTextColor.RED));
            return;
        }

        // A combat logger NPC writes its inventory back to the player's file when it despawns, which would undo the transfer
        for (OfflinePlayer player : new OfflinePlayer[]{oldPlayer, newPlayer}) {
            if (hasCombatLoggerNpc(player)) {
                sender.sendMessage(Component.text(player.getName() + " has an active combat logger NPC,"
                    + " wait for it to despawn and try again", NamedTextColor.RED));
                return;
            }
        }

        sender.sendMessage(Component.text("Transferring inventory from " + oldPlayer.getName()
            + " to " + newPlayer.getName() + "...", NamedTextColor.GRAY));
        this.plugin.getInventoryTransfer().transfer(oldPlayer, newPlayer).whenComplete((itemCount, error) -> {
            if (error != null) {
                Throwable cause = InventoryTransfer.unwrap(error);
                String message = cause instanceof InventoryTransferException ? cause.getMessage() : cause.toString();
                this.plugin.getLogger().log(Level.WARNING, "Failed to transfer inventory from " + oldPlayer.getName()
                    + " (" + oldPlayer.getUniqueId() + ") to " + newPlayer.getName() + " (" + newPlayer.getUniqueId() + ")", cause);
                sender.sendMessage(Component.text("Inventory transfer failed: " + message
                    + ". Groups were not transferred", NamedTextColor.RED));
                return;
            }

            this.plugin.getLogger().info("Transferred " + itemCount + " item stacks from "
                + oldPlayer.getName() + " (" + oldPlayer.getUniqueId() + ") to "
                + newPlayer.getName() + " (" + newPlayer.getUniqueId() + ")");
            sender.sendMessage(Component.text("Transferred " + itemCount + " item stacks from "
                + oldPlayer.getName() + " to " + newPlayer.getName(), NamedTextColor.GREEN));

            // Runs on the main thread, as InventoryTransfer completes on InvSee++'s server thread executor
            transferGroups(sender, oldPlayer, newPlayer);
        });
    }

    private static boolean hasCombatLoggerNpc(OfflinePlayer player) {
        if (!(Bukkit.getPluginManager().getPlugin("CombatTagPlus") instanceof CombatTagPlus combatTag)
            || !combatTag.isEnabled()) {
            return false;
        }
        return combatTag.getNpcManager().npcExists(player.getUniqueId());
    }

    private void transferGroups(CommandSender sender, OfflinePlayer oldPlayer, OfflinePlayer newPlayer) {
        GroupTransfer.Result result = this.plugin.getGroupTransfer().transfer(oldPlayer.getUniqueId(), newPlayer.getUniqueId());

        this.plugin.getLogger().info("Group transfer from " + oldPlayer.getName() + " (" + oldPlayer.getUniqueId() + ") to "
            + newPlayer.getName() + " (" + newPlayer.getUniqueId() + "): joined " + result.joined()
            + ", primary ownership " + result.ownershipTransferred() + ", failed " + result.failed());
        sender.sendMessage(Component.text("Added " + newPlayer.getName() + " to " + result.joined().size() + " groups: "
            + String.join(", ", result.joined()), NamedTextColor.GREEN));
        if (!result.ownershipTransferred().isEmpty()) {
            sender.sendMessage(Component.text("Transferred primary ownership of: "
                + String.join(", ", result.ownershipTransferred()), NamedTextColor.GREEN));
        }
        if (!result.failed().isEmpty()) {
            sender.sendMessage(Component.text("Failed to transfer primary ownership of: "
                + String.join(", ", result.failed()) + ". " + oldPlayer.getName() + " was left in these groups",
                NamedTextColor.RED));
        }
    }

}
