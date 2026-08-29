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
import com.programmerdan.minecraft.banstick.commands.context.BanTarget;
import com.programmerdan.minecraft.banstick.containers.BanResult;
import com.programmerdan.minecraft.banstick.data.BSIP;
import com.programmerdan.minecraft.banstick.handler.BanHandler;
import inet.ipaddr.IPAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * BanStick! BanStick! Ban all the nerds by name, CIDR, IP, or some combo.
 *
 * @author <a href="mailto:programmerdan@gmail.com">ProgrammerDan</a>
 */
@CommandAlias("banstick|bst|stickofdoom|banhammer")
@CommandPermission("banstick.ban")
public class BanStickCommand extends BaseCommand {

    /**
     * Behavior: If given a name or uuid, bans that uuid with a new ban if none exists for that uuid.
     * Returns ban details.
     *
     * <p>If name or uuid with a CIDR postfix, bans that uuid, AND issues a ban against their IP address / subnet.
     *
     * <p>If IP, bans that IP and all players who have used it.
     *
     * <p>If IP/CIDR, bans that IP subnet and all players who have used it.
     */
    @Default
    @Syntax("<ip[/cidr]|name/uuid[/cidr]> [banend: mm/dd/yyyy [hh:mm:ss]] [message]")
    @Description("Swing that banstick, send nerds flying.")
    @CommandCompletion("@banstickPlayers")
    public void onBanStick(CommandSender sender, BanTarget target, String[] rest) {
        String endDate = (rest.length >= 1 ? rest[0] : null);
        String endTime = (rest.length >= 2 ? rest[1] : null);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        SimpleDateFormat combinedFormat = new SimpleDateFormat("MM/dd/yyyy HH:mms:ss");
        Date banEndDate = null;
        Date banEndTime = null;
        Date banEnd = null;
        int messageStart = 0;

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

        String message = (rest.length >= messageStart
            ? String.join(" ", Arrays.copyOfRange(rest, messageStart, rest.length)) : null);

        BanStick.getPlugin().debug("target: {0}, banEnd: {1}, message: {2}", target, banEnd, message);

        if (target.isIp()) {
            if (!sender.hasPermission("banstick.ips")) {
                throw new InvalidCommandArgument("You don't have permission to use / view IPs", false);
            }

            IPAddress ipcheck = target.getIp();
            BSIP exact = !target.hasCidr() ? BSIP.byIPAddress(ipcheck) : BSIP.byCIDR(ipcheck.toString(), target.getCidr());
            if (exact == null) {
                // new IP record.
                exact = target.hasCidr() ? BSIP.create(ipcheck, target.getCidr()) : BSIP.create(ipcheck);
            }

            BanResult result = target.hasCidr() ? BanHandler.doCIDRBan(exact, message, banEnd, true, false) :
                BanHandler.doIPBan(exact, message, banEnd, true, false);

            result.informCommandSender(sender);
        } else {
            UUID playerId = target.getPlayerId();
            BanResult result = null;

            if (target.hasCidr()) { // we only do an IP ban for a player if with CIDR, and then a CIDR on their current IP.
                Player onlineTarget = Bukkit.getPlayer(playerId);

                if (onlineTarget != null) {
                    InetSocketAddress isa = onlineTarget.getAddress();
                    InetAddress na = isa != null ? isa.getAddress() : null;

                    // target's address is @nullable so we need to explicitly handle that.
                    if (na != null) {
                        BSIP exact = BSIP.byCIDR(na, target.getCidr());
                        if (exact == null) {
                            // new IP record.
                            exact = BSIP.create(na, target.getCidr());
                        }

                        result = BanHandler.doCIDRBan(exact, message, banEnd, true, false);
                        result.informCommandSender(sender);
                    }
                }
            }

            result = BanHandler.doUUIDBan(playerId, message, banEnd, true);
            result.informCommandSender(sender);
        }
    }
}
