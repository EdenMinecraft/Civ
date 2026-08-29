package com.programmerdan.minecraft.banstick.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import com.programmerdan.minecraft.banstick.BanStick;
import com.programmerdan.minecraft.banstick.commands.context.BanStickContexts;
import com.programmerdan.minecraft.banstick.commands.context.BanTarget;
import com.programmerdan.minecraft.banstick.containers.BanResult;
import com.programmerdan.minecraft.banstick.data.BSIP;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSSession;
import com.programmerdan.minecraft.banstick.data.BSShare;
import com.programmerdan.minecraft.banstick.handler.BanHandler;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Always finish with a DoubleTap to the head -- handles manually unpardoning and perhaps banning nerds who multiaccount
 *
 * <p>This command's grammar is inherently branching (one IP-shaped token, or two
 * player-shaped tokens) so it doesn't reduce to a fixed set of typed ACF
 * parameters the way most of the other commands in this package do -- it
 * still reuses {@link BanStickContexts}'s shared resolvers though, rather than
 * hand-rolling its own parsing.
 *
 * @author <a href="mailto:programmerdan@gmail.com">ProgrammerDan</a>
 */
@CommandAlias("doubletap|bdt|doublebladedaxe")
@CommandPermission("banstick.ban")
public class DoubleTapCommand extends BaseCommand {

    /**
     * <b>doubletap [+][ip] [banend: mm/dd/yyyy [hh:mm:ss]] [message]</b>
     * Finds all sessions that use this IP, finds all Shares connected to those Sessions, unpardons any pardoned shares.
     * If [+] before the IP, also Share-bans all non-pardoned players.
     * <br>
     * <b>doubletap [+][name/uuid] [+][name/uuid] [banend: mm/dd/yyyy [hh:mm:ss]] [message]</b>
     * Finds all Shares between the two named players, unpardons them if pardoned.
     * If [+] before a name, also Share-bans that player, removing any Share pardons if they existed.
     */
    @Default
    @Syntax("<[+]ip|[+]name/uuid [+]name/uuid> [banend: mm/dd/yyyy [hh:mm:ss]] [message]")
    @Description("Handle unpardoning shares between two players; optionally ban one or both")
    public void onDoubleTap(CommandSender sender, String[] arguments) {
        if (arguments.length < 1) {
            throw new InvalidCommandArgument();
        }

        BanTarget first = BanStickContexts.parseBanTarget(arguments[0]);
        BanTarget second = null;
        int offset = 0;

        if (!first.isIp()) {
            if (arguments.length < 2) {
                throw new InvalidCommandArgument();
            }
            second = BanStickContexts.parseBanTarget(arguments[1]);
            offset = 1;
        }

        String endDate = (arguments.length >= (2 + offset) ? arguments[1 + offset] : null);
        String endTime = (arguments.length >= (3 + offset) ? arguments[2 + offset] : null);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        SimpleDateFormat combinedFormat = new SimpleDateFormat("MM/dd/yyyy HH:mms:ss");
        Date banEndDate = null;
        Date banEndTime = null;
        Date banEnd = null;
        int messageStart = 1 + offset;

        BanStick.getPlugin().debug(
            "first: {0}, second: {1}, endDate: {2}, endTime: {3}", first, second, endDate, endTime);

        if (endDate != null) {
            try {
                banEndDate = dateFormat.parse(endDate);
                banEnd = banEndDate;
                messageStart++;
            } catch (ParseException pe) {
                banEndDate = null;
            }

            if (banEndDate != null && endTime != null) {
                try {
                    banEndTime = combinedFormat.parse(endDate + " " + endTime);
                    banEnd = banEndTime;
                    messageStart++;
                } catch (ParseException pe) {
                    banEndTime = null;
                }
            }
        }

        String message = (arguments.length >= messageStart ? String.join(" ",
            Arrays.copyOfRange(arguments, messageStart, arguments.length)) : null);

        BanStick.getPlugin().debug("message: {0}", message);

        if (first.isIp()) {
            if (!sender.hasPermission("banstick.ips")) {
                throw new InvalidCommandArgument("You don't have permission to use / view IPs", false);
            }

            BSIP exact = BSIP.byIPAddress(first.getIp());
            if (exact == null) {
                sender.sendMessage(ChatColor.YELLOW + "That IP address not found, no shares modified.");
                return;
            }

            List<BSSession> sessions = BSSession.byIP(exact);

            if (sessions == null || sessions.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "That IP address does not connect to any sessions.");
                return;
            }

            int unpardoned = 0;
            int banned = 0;
            for (BSSession session : sessions) {
                List<BSShare> shares = BSShare.bySession(session);
                for (BSShare share : shares) {
                    if (share.isPardoned()) {
                        share.setPardonTime(null);
                        unpardoned++;
                        sender.sendMessage(ChatColor.GREEN + "Unpardoned Shared session: "
                            + share.toFullString(sender.hasPermission("banstick.ips")));
                    }
                    if (first.hasPlusFlag()) {
                        BanResult result = BanHandler.doShareBan(share, null, message, banEnd, true);
                        result.informCommandSender(sender);
                        banned++;
                    }
                }
            }
            sender.sendMessage(ChatColor.GREEN + "Unpardoned " + unpardoned + " shared and attempted "
                + banned + " share bans.");
        } else {
            UUID playerId = first.getPlayerId();
            UUID secondPlayerId = second.getPlayerId();

            BSPlayer player1 = BSPlayer.byUUID(playerId);
            BSPlayer player2 = BSPlayer.byUUID(secondPlayerId);

            if (player1 != null && player2 != null) {
                List<BSShare> shares = player1.sharesWith(player2);
                if (shares != null && !shares.isEmpty()) {
                    for (BSShare share : shares) {
                        if (share.isPardoned()) {
                            share.setPardonTime(null);
                            sender.sendMessage(ChatColor.GREEN + "Unpardoned Shared session: "
                                + share.toFullString(sender.hasPermission("banstick.ips")));
                        }
                    }
                    if (first.hasPlusFlag() || second.hasPlusFlag()) {
                        BanResult result = BanHandler.doShareBan(shares.get(shares.size() - 1),
                            first.hasPlusFlag() && !second.hasPlusFlag() ? player1
                                : !first.hasPlusFlag() && second.hasPlusFlag() ? player2 : null,
                            message, banEnd, true);
                        result.informCommandSender(sender);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Those players have not shared a connection.");
                }
            } else {
                if (player1 == null) {
                    sender.sendMessage(ChatColor.RED + "Unable to find " + ChatColor.DARK_RED + playerId);
                }
                if (player2 == null) {
                    sender.sendMessage(ChatColor.RED + "Unable to find " + ChatColor.DARK_RED + secondPlayerId);
                }
            }
        }
    }
}
