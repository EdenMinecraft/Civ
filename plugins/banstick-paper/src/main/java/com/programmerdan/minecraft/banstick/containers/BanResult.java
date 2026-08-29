package com.programmerdan.minecraft.banstick.containers;

import com.programmerdan.minecraft.banstick.data.BSBan;
import com.programmerdan.minecraft.banstick.data.BSIP;
import com.programmerdan.minecraft.banstick.data.BSIPData;
import com.programmerdan.minecraft.banstick.data.BSShare;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.command.CommandSender;

/**
 * Used to store bans issued and then transmit the results to various parties.
 * Basically a logic wrapper.
 *
 * <p>Direct player bans are enacted by banstick-velocity (see
 * {@link com.programmerdan.minecraft.banstick.handler.BanHandler}), so this class
 * only carries the display summary of what was banned rather than a live
 * {@code BSPlayer}/{@code BSBan} pair for that case.
 *
 * @author <a href="mailto:programmerdan@gmail.com">ProgrammerDan</a>
 */
public class BanResult {

    /**
     * Display summary of one direct player ban that was successfully forwarded to
     * banstick-velocity.
     */
    public record PlayerBanSummary(String playerName, String message, Date banEnd) {
    }

    private List<PlayerBanSummary> playerBans;
    private Set<BSBan> bans;

    public BanResult() {
        playerBans = new ArrayList<>();
        bans = new HashSet<>();
    }

    public static SimpleDateFormat getUsualDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    /**
     * Let the command sender know the result of the ban(s) and player(s).
     *
     * @param sender the sender of commands.
     */
    public void informCommandSender(CommandSender sender) {
        if (bans.isEmpty() && playerBans.isEmpty()) {
            sender.sendMessage("No bans issued.");
        }
        StringBuilder sb = new StringBuilder();
        if (playerBans.size() > 1) {
            sb.append(playerBans.size()).append(" player bans issued.\n");
        }
        for (PlayerBanSummary banned : playerBans) {
            sb.append(" Banned ").append(banned.playerName()).append(" for ").append(banned.message());
            if (banned.banEnd() != null) {
                sb.append(" until ").append(getUsualDateTime().format(banned.banEnd())).append("\n");
            } else {
                sb.append(" forever\n");
            }
        }
        if (bans.size() > 1) {
            sb.append(bans.size()).append(" other bans issued.\n");
        }
        for (BSBan banned : bans) {
            sb.append(" Banned ");
            BSIP bip = banned.getIPBan();
            if (bip != null) {
                sb.append(" IP ").append(bip.toFullString(sender.hasPermission("banstick.ips")));
            }
            BSIPData vip = banned.getProxyBan();
            if (vip != null) {
                sb.append(" VPN ").append(vip.toFullString(sender.hasPermission("banstick.ips")));
            }
            BSShare sid = banned.getShareBan();
            if (sid != null) {
                sb.append(" Share ").append(sid.toFullString(sender.hasPermission("banstick.ips")));
            }
            if (banned.getBanEndTime() != null) {
                sb.append(" until ").append(getUsualDateTime().format(banned.getBanEndTime())).append("\n");
            } else {
                sb.append(" forever\n");
            }
        }
        sender.sendMessage(sb.toString());
    }

    public void addPlayerBan(String playerName, String message, Date banEnd) {
        playerBans.add(new PlayerBanSummary(playerName, message, banEnd));
    }

    public void addBan(BSBan ban) {
        bans.add(ban);
    }
}
