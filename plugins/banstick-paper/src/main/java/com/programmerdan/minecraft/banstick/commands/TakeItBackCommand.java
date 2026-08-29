package com.programmerdan.minecraft.banstick.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import com.programmerdan.minecraft.banstick.handler.BanHandler;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSShare;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vg.civcraft.mc.namelayer.NameLayerAPI;

/**
 * TakeItBackCommand, for when you pardoned someone but regretted it
 *
 * @author <a href="mailto:programmerdan@gmail.com">ProgrammerDan</a>
 */
// TODO: Unsafe command structure as player could have name of IP or PROXY or SHARED
//    and that would break this command.
@CommandAlias("takeitback|unforgive|buryhim")
@CommandPermission("banstick.forgive")
public class TakeItBackCommand extends BaseCommand {

    /**
     * <b>takeitback [name/uuid] [IP] [PROXY] [SHARED]</b>
     * IP - Allows new bans on this player due to IP matches
     * PROXY - Allows new bans on this player due to IPData ban matches
     * SHARED - Allows new bans on this player due to Share connection ban matches
     * <br>
     * <b>takeitback [name/uuid] [name/uuid]</b>
     * Immediately unpardons all shares between these two players.
     */
    @Default
    @Syntax("<name/uuid> <IP|PROXY|SHARED ...>  |  <name/uuid> <name/uuid>")
    @Description("Remove exemptions/ignores with this command.")
    @CommandCompletion("@banstickPlayers @banPardonTypes")
    public void onTakeItBack(CommandSender sender, BSPlayer player, String secRevoke, String[] rest) {
        UUID playerId = player.getUUID();

        UUID secondPlayerId = null;
        if (secRevoke.length() <= 16) {
            try {
                secondPlayerId = null;
                try {
                    secondPlayerId = NameLayerAPI.getUUID(secRevoke);
                } catch (NoClassDefFoundError ncde) {
                }

                if (secondPlayerId == null) {
                    Player match = Bukkit.getPlayer(secRevoke);
                    if (match != null) {
                        secondPlayerId = match.getUniqueId();
                    }
                }
            } catch (Exception ee) {
                // not a player, but might be a pardon
            }
        } else if (secRevoke.length() == 36) {
            try {
                secondPlayerId = UUID.fromString(secRevoke);
            } catch (IllegalArgumentException iae) {
                throw new InvalidCommandArgument("Unable to process uuid " + secRevoke);
            }
        } else {
            throw new InvalidCommandArgument("Unable to interpret " + secRevoke);
        }

        if (secondPlayerId == null) { // single player unpardon.
            List<String> revokes = new ArrayList<>(rest.length + 1);
            revokes.add(secRevoke);
            revokes.addAll(Arrays.asList(rest));

            boolean match = false;
            for (String pardon : revokes) {
                if ("IP".equalsIgnoreCase(pardon)) {
                    if (BanHandler.getPlayerStatus(playerId).ipPardonTime() != null) {
                        BanHandler.clearIPPardon(playerId);
                        sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                            + " is exposed to future IP bans. Existing bans aren't impacted.");
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                            + " is already exposed to IP bans.");
                    }
                    match = true;
                }

                if ("PROXY".equalsIgnoreCase(pardon)) {
                    if (BanHandler.getPlayerStatus(playerId).proxyPardonTime() != null) {
                        BanHandler.clearProxyPardon(playerId);
                        sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                            + " is exposed to future Proxy bans. Existing warnings aren't impacted.");
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                            + " is already exposed to Proxy bans.");
                    }
                    match = true;
                }

                if ("SHARED".equalsIgnoreCase(pardon)) {
                    if (BanHandler.getPlayerStatus(playerId).sharedPardonTime() != null) {
                        BanHandler.clearSharedPardon(playerId);
                        sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                            + " is exposed to future Share warnings/bans. Existing warning/bans aren't impacted.");
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                            + " is already exposed to Share warnings/bans.");
                    }
                    match = true;
                }
            }

            if (!match) {
                throw new InvalidCommandArgument("Could not determine what to do.", false);
            }
        } else {
            // unpardon shares between two people
            BSPlayer player2 = BSPlayer.byUUID(secondPlayerId);
            List<BSShare> shares = player.sharesWith(player2);
            int pardonsRevoked = 0;
            if (shares != null && !shares.isEmpty()) {
                for (BSShare share : shares) {
                    if (share.isPardoned()) {
                        share.setPardonTime(null);
                        pardonsRevoked++;
                    }
                }
                if (pardonsRevoked > 0) {
                    sender.sendMessage(ChatColor.GREEN + "Revoked pardons for " + pardonsRevoked + " shared sessions");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Found no shared sessions needing pardon revocation");
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Player " + player.getName()
                    + " does not share any connections with " + player2.getName());
            }
        }
    }

}
