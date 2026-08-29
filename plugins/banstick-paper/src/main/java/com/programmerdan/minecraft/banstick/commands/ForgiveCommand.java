package com.programmerdan.minecraft.banstick.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import com.programmerdan.minecraft.banstick.BanStick;
import com.programmerdan.minecraft.banstick.commands.context.BanStickContexts;
import com.programmerdan.minecraft.banstick.commands.context.BanTarget;
import com.programmerdan.minecraft.banstick.handler.BanHandler;
import com.programmerdan.minecraft.banstick.data.BSBan;
import com.programmerdan.minecraft.banstick.data.BSIP;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSShare;
import inet.ipaddr.IPAddress;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bukkit.BanList;
import org.bukkit.BanList.Type;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The best thing is forgiveness. This command can pardon most kinds of bans.
 *
 * @author <a href="mailto:programmerdan@gmail.com">ProgrammerDan</a>
 */
@CommandAlias("forgive|bstf|showmercy")
@CommandPermission("banstick.forgive")
public class ForgiveCommand extends BaseCommand {

    @Default
    @Syntax("<ip[/cidr]|name/uuid> [BAN|IP|PROXY|SHARED ...] | <name/uuid> <name/uuid> [ALL]")
    @Description("Remove specific IP / CIDR bans, unban specific individuals, or grant exemptions/ignores for a user")
    @CommandCompletion("@banstickPlayers @banPardonTypes")
    public void onForgive(CommandSender sender, BanTarget target, String[] pardons) {
        BanStick.getPlugin().debug("target: {0}, pardons: {1}", target, pardons);

        if (target.isIp()) {
            if (!sender.hasPermission("banstick.ips")) {
                throw new InvalidCommandArgument("You don't have permission to use / view IPs", false);
            }

            IPAddress ipcheck = target.getIp();
            BSIP exact = !target.hasCidr() ? BSIP.byIPAddress(ipcheck) : BSIP.byCIDR(ipcheck.toString(), target.getCidr());
            if (exact == null) {
                sender.sendMessage(ChatColor.RED + "Can't find " + (target.hasCidr() ? ipcheck.toString()
                    + "/" + target.getCidr() : ipcheck.toString()));
                return;
            }

            List<BSBan> bans = BSBan.byIP(exact, false);

            int banLifted = 0;
            for (BSBan ban : bans) {
                ban.setBanEndTime(new Date());
                banLifted++;
            }

            sender.sendMessage(ChatColor.GREEN + "Forgave " + banLifted + " active bans");

            try {
                Bukkit.unbanIP(ipcheck.toString());
                BanStick.getPlugin().debug("Also forgave any underlying bukkit ban on IP");
            } catch (Exception e) {
                BanStick.getPlugin().debug("Failed to forgive any underlying bukkit ban on IP");
            }
            return;
        }

        UUID playerId = target.getPlayerId();
        BSPlayer player = BSPlayer.byUUID(playerId);

        if (pardons.length == 0) { // unban
            unban(sender, playerId, player);
            return;
        }

        boolean match = false;
        for (String pardon : pardons) {
            if ("BAN".equalsIgnoreCase(pardon)) {
                unban(sender, playerId, player);
                match = true;
            }

            if ("IP".equalsIgnoreCase(pardon)) {
                if (BanHandler.getPlayerStatus(playerId).ipPardonTime() == null) {
                    BanHandler.setIPPardon(playerId);
                    sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                        + " is pardoned from future IP bans. Existing bans aren't impacted.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                        + " is already pardoned from IP bans.");
                }
                match = true;
            }

            if ("PROXY".equalsIgnoreCase(pardon)) {
                if (BanHandler.getPlayerStatus(playerId).proxyPardonTime() == null) {
                    BanHandler.setProxyPardon(playerId);
                    sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                        + " is pardoned from future Proxy bans. Existing warnings aren't impacted.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                        + " is already pardoned from Proxy bans.");
                }
                match = true;
            }

            if ("SHARED".equalsIgnoreCase(pardon)) {
                if (BanHandler.getPlayerStatus(playerId).sharedPardonTime() == null) {
                    BanHandler.setSharedPardon(playerId);
                    sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                        + " is pardoned from future Share warnings/bans."
                        + " Existing warning/bans aren't impacted.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Player " + player.getName()
                        + " is already pardoned from Share warnings/bans.");
                }
                match = true;
            }
        }

        if (match) {
            return;
        }

        // Not a recognized pardon keyword -- try "forgive <player> <player> [ALL]" share-pardon mode.
        UUID playerId2 = BanStickContexts.resolvePlayerUuid(pardons[0]);
        BSPlayer player2 = BSPlayer.byUUID(playerId2);
        List<BSShare> shares = player.sharesWith(player2);
        int banLifted = 0;
        int pardonsGranted = 0;
        if (shares != null && !shares.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Checking " + shares.size()
                + " shared sessions for ones needing pardon");
            boolean alsoUnban = pardons.length > 1 && "ALL".equalsIgnoreCase(pardons[1]);
            for (BSShare share : shares) {
                if (alsoUnban) {
                    List<BSBan> bans = BSBan.byShare(share, false);
                    for (BSBan ban : bans) {
                        ban.setBanEndTime(new Date());
                        banLifted++;
                    }
                }

                if (!share.isPardoned()) {
                    share.setPardonTime(new Date());
                    pardonsGranted++;
                }
            }
            if (alsoUnban && banLifted > 0) {
                sender.sendMessage(ChatColor.GREEN + "Forgave " + banLifted + " active bans");
            } else if (alsoUnban) {
                sender.sendMessage(ChatColor.YELLOW
                    + "Found no bans due to shared sessions to forgive");
            }
            if (pardonsGranted > 0) {
                sender.sendMessage(ChatColor.GREEN + "Pardoned "
                    + pardonsGranted + " shared sessions");
            } else {
                sender.sendMessage(ChatColor.YELLOW
                    + "Found no shared sessions still needing pardon");
            }
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Player " + player.getName()
                + " does not share any connections with " + player2.getName());
        }
    }

    private void unban(CommandSender sender, UUID playerId, BSPlayer player) {
        BanHandler.UnbanResult result = BanHandler.doUnban(playerId);
        if (result.success()) {
            if (result.wasBanned()) {
                sender.sendMessage(ChatColor.GREEN + "Player " + player.getName() + " is unbanned.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Player " + player.getName() + " is already unbanned.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to reach banstick-velocity to unban "
                + player.getName());
        }

        try {
            Player underlyingUnban = Bukkit.getPlayer(playerId);
            if (underlyingUnban != null && underlyingUnban.isBanned()) {
                BanList legacyBans = Bukkit.getBanList(Type.NAME);
                legacyBans.pardon(playerId.toString());
                legacyBans.pardon(player.getName());
                BanStick.getPlugin().debug("Also forgave any underlying bukkit ban on uuid / player name");
            } else {
                BanStick.getPlugin().debug("Underlying bukkit ban might remain on uuid / player name");
            }
        } catch (Exception q) {
            BanStick.getPlugin().debug("Failed to forgive any underlying bukkit ban on uuid / player name");
        }
    }

}
