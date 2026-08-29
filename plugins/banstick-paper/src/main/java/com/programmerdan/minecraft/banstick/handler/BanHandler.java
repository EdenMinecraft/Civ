package com.programmerdan.minecraft.banstick.handler;

import com.programmerdan.minecraft.banstick.BanStick;
import com.programmerdan.minecraft.banstick.containers.BanResult;
import com.programmerdan.minecraft.banstick.data.BSBan;
import com.programmerdan.minecraft.banstick.data.BSIP;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSSession;
import com.programmerdan.minecraft.banstick.data.BSShare;
import com.programmerdan.minecraft.banstick.redis.BanStickRedisClient;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vg.civcraft.mc.namelayer.NameLayerAPI;
import world.edenmc.banstickapi.AckResponse;
import world.edenmc.banstickapi.BanStatusResponse;
import world.edenmc.banstickapi.PardonType;

/**
 * A series of static utility classes to facilitate issuing bans.
 *
 * <p>banstick-velocity is the single owner of direct-player ban state
 * (bs_player.bid / bs_ban / bs_ban_log), so every path below that ultimately bans a
 * specific player forwards that action to it over Redis instead of writing
 * locally. Local {@link BSBan} rows tied to an IP/proxy/share are still created
 * here as before -- those remain Paper-side heuristic/audit data used to detect
 * future ban-worthy connections, independent of any one player's active ban.
 *
 * @author <a href="mailto:programmerdan@gmail.com">ProgrammerDan</a>
 */
public final class BanHandler {

    private static final String AUTO_BAN = "Automatic Ban";
    private static final String ADMIN_BAN = "Administrative Ban";

    private static BanStickRedisClient redisClient;

    private BanHandler() {
    }

    /**
     * Wires up the Redis client used to talk to banstick-velocity. Called once
     * from {@link BanStick#onEnable()}.
     */
    public static void setRedisClient(BanStickRedisClient client) {
        redisClient = client;
    }

    private static BanStickRedisClient redisClient() {
        if (redisClient == null) {
            throw new IllegalStateException("BanStick Redis client not initialized");
        }
        return redisClient;
    }

    private static SimpleDateFormat getEndTimeFormat() {
        return new SimpleDateFormat("MM/dd/yyyy HH:mms:ss");
    }

    /**
     * Asks banstick-velocity to ban a specific UUID, kicking them locally if
     * they're online on this server.
     *
     * @param playerId The UUID of the player to ban.
     * @param message  The message to use, or null/blank for the default.
     * @param banEnd   The time the ban should end, or null for permanent.
     * @param adminBan Was this admin ban or automatic?
     * @return A summary of who was banned.
     */
    private static boolean requestBan(UUID playerId, String playerName, String message, Date banEnd, boolean adminBan) {
        Long banEndMillis = banEnd != null ? banEnd.getTime() : null;
        return redisClient().issueBan(playerId, playerName, message, banEndMillis, adminBan);
    }

    /**
     * Asks banstick-velocity to enact a ban that was locally detected (IP/subnet/
     * proxy/share heuristics), using an already-created local {@link BSBan} as the
     * source of the message/expiry/admin flag. The local {@code ban} row itself
     * (and its IP/proxy/share linkage) stays as Paper-side reference data for
     * future heuristic matching; this call is what actually makes the ban take
     * effect for {@code player} everywhere, since banstick-velocity is the sole
     * owner of bs_player.bid.
     *
     * @param player The player to ban.
     * @param ban    The locally-created ban record describing why.
     * @return true if banstick-velocity acknowledged the ban.
     */
    public static boolean requestBan(BSPlayer player, BSBan ban) {
        return requestBan(player.getUUID(), player.getName(), ban.getMessage(), ban.getBanEndTime(), ban.isAdminBan());
    }

    /**
     * Authoritative ban + pardon status for a player, straight from
     * banstick-velocity. banstick-velocity is the sole owner of these fields, so
     * this is always fresh -- unlike reading a locally-cached {@link BSPlayer}.
     */
    public record PlayerStatus(boolean banned, String banMessage, Date banEnd, boolean adminBan,
                                Date ipPardonTime, Date proxyPardonTime, Date sharedPardonTime) {

        private static final PlayerStatus EMPTY = new PlayerStatus(false, null, null, false, null, null, null);

        private static Date epochToDate(Long epochMs) {
            return epochMs != null ? new Date(epochMs) : null;
        }

        static PlayerStatus from(BanStatusResponse r) {
            return new PlayerStatus(r.banned(), r.message(), epochToDate(r.banEndEpochMs()), r.adminBan(),
                epochToDate(r.ipPardonEpochMs()), epochToDate(r.proxyPardonEpochMs()), epochToDate(r.sharedPardonEpochMs()));
        }
    }

    /**
     * Asks banstick-velocity for a player's ban + pardon status. On timeout/error
     * (logged), returns an all-empty status rather than throwing -- callers should
     * treat that the same as "unknown," not "definitely not banned/pardoned."
     */
    public static PlayerStatus getPlayerStatus(UUID playerId) {
        return redisClient().getStatus(playerId).map(PlayerStatus::from).orElse(PlayerStatus.EMPTY);
    }

    private static boolean setPardon(UUID playerId, String pardonType, Date time) {
        return redisClient().setPardon(playerId, pardonType, time != null ? time.getTime() : null);
    }

    public static boolean setIPPardon(UUID playerId) {
        return setPardon(playerId, PardonType.IP, new Date());
    }

    public static boolean setProxyPardon(UUID playerId) {
        return setPardon(playerId, PardonType.PROXY, new Date());
    }

    public static boolean setSharedPardon(UUID playerId) {
        return setPardon(playerId, PardonType.SHARED, new Date());
    }

    public static boolean clearIPPardon(UUID playerId) {
        return setPardon(playerId, PardonType.IP, null);
    }

    public static boolean clearProxyPardon(UUID playerId) {
        return setPardon(playerId, PardonType.PROXY, null);
    }

    public static boolean clearSharedPardon(UUID playerId) {
        return setPardon(playerId, PardonType.SHARED, null);
    }

    /**
     * Issues a ban against a specific UUID.
     *
     * <p>If the player is online, kicks them.
     *
     * <p>This uses the default message. See {@link #doUUIDBan(UUID, String, boolean)} for custom message,
     * or {@link #doUUIDBan(UUID, Date, boolean)} for end time, or {@link #doUUIDBan(UUID, String, Date, boolean)} for
     * both.
     *
     * @param playerId The UUID of the player to ban.
     * @param adminBan Was this admin ban or automatic?
     * @return A summary of who was banned.
     */
    public static BanResult doUUIDBan(UUID playerId, boolean adminBan) {
        return doUUIDBan(playerId, null, null, adminBan);
    }

    /**
     * Issues a ban against a specific UUID.
     *
     * <p>If the player is online, kicks them.
     *
     * <p>This uses the default message but a custom end time.
     *
     * @param playerId The UUID of the player to ban.
     * @param banEnd   The time the ban should end.
     * @param adminBan Was this admin ban or automatic?
     * @return A summary of who was banned.
     */
    public static BanResult doUUIDBan(UUID playerId, Date banEnd, boolean adminBan) {
        return doUUIDBan(playerId, null, banEnd, adminBan);
    }

    /**
     * Issues a ban against a specific UUID.
     *
     * <p>If the player is online, kicks them.
     *
     * <p>This uses a custom message.
     *
     * @param playerId The UUID of the player to ban.
     * @param message  The message to display when a player attempts to rejoin.
     * @param adminBan Was this admin ban or automatic?
     * @return A summary of who was banned.
     */
    public static BanResult doUUIDBan(UUID playerId, String message, boolean adminBan) {
        return doUUIDBan(playerId, message, null, adminBan);
    }

    /**
     * Issues a ban against a specific UUID.
     *
     * <p>If the player is online, kicks them.
     *
     * <p>This uses a custom message and end time.
     *
     * @param playerId The UUID of the player to ban.
     * @param message  The message to display when a player attempts to rejoin.
     * @param banEnd   The time the ban should end.
     * @param adminBan Was this admin ban or automatic?
     * @return A summary of who was banned.
     */
    public static BanResult doUUIDBan(UUID playerId, String message, Date banEnd, boolean adminBan) {
        BanResult result = new BanResult();
        try {
            if (message == null || message.trim().equals("")) {
                message = adminBan ? ADMIN_BAN : AUTO_BAN; // TODO: config!
            }
            Player spigotPlayer = Bukkit.getPlayer(playerId);
            String playerName = spigotPlayer != null ? spigotPlayer.getName() : null;
            if (playerName == null) {
                try {
                    playerName = NameLayerAPI.getCurrentName(playerId);
                } catch (NoClassDefFoundError ncde) {
                    // no namelayer
                }
            }

            boolean success = requestBan(playerId, playerName, message, banEnd, adminBan);
            if (!success) {
                BanStick.getPlugin().warning("banstick-velocity failed to ban {0}", playerId);
                return result;
            }
            result.addPlayerBan(playerName != null ? playerName : playerId.toString(), message, banEnd);

            if (spigotPlayer != null) {
                if (banEnd != null) {
                    spigotPlayer.kickPlayer(message + ". Ends " + BanHandler.getEndTimeFormat().format(banEnd));
                } else {
                    spigotPlayer.kickPlayer(message);
                }
            }

            return result;
        } catch (Exception e) {
            BanStick.getPlugin().warning("Failed to issue UUID ban: ", e);
            return result;
        }
    }

    /**
     * Issues a ban against an IP address.
     * After the ban is created, finds all accounts that are using the IP address and bans them, unless
     * already banned or pardoned.
     *
     * @param exactIP         The IP address to ban.
     * @param message         The message to use as a ban message; is also sent to all players who are
     *                        online and caught in the ban.
     * @param banEnd          When does the ban end?
     * @param adminBan        Was this an administrative ban?
     * @param includeHistoric Ban everyone who has ever used this IP address?
     * @return A BanResult object describing who was banned.
     */
    public static BanResult doIPBan(BSIP exactIP, String message, Date banEnd,
                                    boolean adminBan, boolean includeHistoric) {
        try {
            if (message == null || message.trim().equals("")) {
                message = adminBan ? ADMIN_BAN : AUTO_BAN; // TODO: config!
            }
            // TODO: match with existing ban for this IP.
            BSBan ban = BSBan.create(exactIP, message, banEnd, adminBan); // general ban.
            BanResult result = new BanResult();
            result.addBan(ban);

            for (Player player : Bukkit.getOnlinePlayers()) {
                BSPlayer banPlayer = BSPlayer.byUUID(player.getUniqueId());
                PlayerStatus status = getPlayerStatus(banPlayer.getUUID());
                if (status.ipPardonTime() != null || status.banned()) {
                    continue; // pardoned from IP match bans, or already banned.
                }
                BSSession active = banPlayer.getLatestSession();
                if (active.getIP().getId() == exactIP.getId()) {
                    // TODO replace with equality check.
                    if (requestBan(banPlayer.getUUID(), banPlayer.getName(), message, banEnd, adminBan)) {
                        result.addPlayerBan(banPlayer.getName(), message, banEnd);
                        if (banEnd != null) {
                            player.kickPlayer(message + ". Ends " + BanHandler.getEndTimeFormat().format(banEnd));
                        } else {
                            player.kickPlayer(message);
                        }
                    }
                }
            }

            if (includeHistoric) {
                List<BSSession> sessions = BSSession.byIP(exactIP);
                for (BSSession session : sessions) {
                    BSPlayer banPlayer = session.getPlayer();
                    PlayerStatus status = getPlayerStatus(banPlayer.getUUID());
                    if (status.ipPardonTime() != null || status.banned()) {
                        continue; // pardoned from IP match bans, or already banned.
                    }
                    if (requestBan(banPlayer.getUUID(), banPlayer.getName(), message, banEnd, adminBan)) {
                        result.addPlayerBan(banPlayer.getName(), message, banEnd);
                    }
                }
            }

            return result;
        } catch (Exception e) {
            BanStick.getPlugin().warning("Failed to issue IP ban: ", e);
            return new BanResult();
        }
    }

    /**
     * Does a ban against a CIDR range.
     *
     * @param cidrIP          cidr IP range to ban
     * @param message         Message to record as ban reason
     * @param banEnd          The time to end the ban
     * @param adminBan        Is this an administrative ban?
     * @param includeHistoric Should we include all historic occurrences of this IP in the ban?
     * @return A BanResult with the bans issued, if any
     */
    public static BanResult doCIDRBan(BSIP cidrIP, String message, Date banEnd,
                                      boolean adminBan, boolean includeHistoric) {
        try {
            if (message == null || message.trim().equals("")) {
                message = adminBan ? ADMIN_BAN : AUTO_BAN; // TODO: config!
            }
            BSBan ban = BSBan.create(cidrIP, message, banEnd, adminBan); // general ban.
            BanResult result = new BanResult();
            result.addBan(ban);

            for (Player player : Bukkit.getOnlinePlayers()) {
                BSPlayer banPlayer = BSPlayer.byUUID(player.getUniqueId());
                PlayerStatus status = getPlayerStatus(banPlayer.getUUID());
                if (status.banned()) {
                    continue; // already banned.
                }
                if (status.ipPardonTime() != null) {
                    continue; // pardoned from IP match bans.
                }

                BSSession active = banPlayer.getLatestSession();
                BSIP activeIP = active.getIP();
                boolean doBan = false;
                if (cidrIP.getIPv4Address() != null && activeIP.getIPv4Address() != null) {
                    // check IPv4
                    if (cidrIP.getIPv4Address().contains(activeIP.getIPv4Address())) {
                        doBan = true;
                    }
                } else if (cidrIP.getIPv6Address() != null && activeIP.getIPv6Address() != null) {
                    if (cidrIP.getIPv6Address().contains(activeIP.getIPv6Address())) {
                        doBan = true;
                    }
                } // if mismatched, don't ban.

                if (doBan && requestBan(banPlayer.getUUID(), banPlayer.getName(), message, banEnd, adminBan)) {
                    result.addPlayerBan(banPlayer.getName(), message, banEnd);
                    if (banEnd != null) {
                        player.kickPlayer(message + ". Ends " + BanHandler.getEndTimeFormat().format(banEnd));
                    } else {
                        player.kickPlayer(message);
                    }
                }
            }

            if (includeHistoric) {
                List<BSIP> ipsIn = BSIP.allContained(cidrIP.getIPAddress().getLower(),
                    cidrIP.getIPAddress().getNetworkPrefixLength());
                for (BSIP exactIP : ipsIn) {
                    List<BSSession> sessions = BSSession.byIP(exactIP);
                    for (BSSession session : sessions) {
                        BSPlayer banPlayer = session.getPlayer();
                        PlayerStatus status = getPlayerStatus(banPlayer.getUUID());
                        if (status.ipPardonTime() != null || status.banned()) {
                            continue; // pardoned from IP match bans, or already banned.
                        }
                        if (requestBan(banPlayer.getUUID(), banPlayer.getName(), message, banEnd, adminBan)) {
                            result.addPlayerBan(banPlayer.getName(), message, banEnd);
                        }
                    }
                }
            }

            return result;
        } catch (Exception e) {
            BanStick.getPlugin().warning("Failed to issue CIDR ban: ", e);
            return new BanResult();
        }
    }

    /**
     * Given a share, ban both (or if limitBanTo is set, just one) with a specified message / end / admin flag
     *
     * @param share      The share to ban
     * @param limitBanTo optional player to limit to
     * @param message    the ban message
     * @param banEnd     the end of the ban
     * @param adminBan   is this an admin ban?
     * @return the result of the ban as a BanResult
     */
    public static BanResult doShareBan(BSShare share, BSPlayer limitBanTo, String message,
                                       Date banEnd, boolean adminBan) {
        try {
            if (message == null || message.trim().equals("")) {
                message = adminBan ? ADMIN_BAN : AUTO_BAN; // TODO: config!
            }
            BSBan ban = BSBan.create(share, message, banEnd, adminBan); // share ban
            BanResult result = new BanResult();
            result.addBan(ban);
            BSPlayer first = share.getFirstPlayer();
            BSPlayer second = share.getSecondPlayer();
            if (limitBanTo == null) { // do both
                PlayerStatus firstStatus = getPlayerStatus(first.getUUID());
                if (firstStatus.sharedPardonTime() == null && !firstStatus.banned()) {
                    if (requestBan(first.getUUID(), first.getName(), message, banEnd, adminBan)) {
                        result.addPlayerBan(first.getName(), message, banEnd);
                    }
                }
                PlayerStatus secondStatus = getPlayerStatus(second.getUUID());
                if (secondStatus.sharedPardonTime() == null && !secondStatus.banned()) {
                    if (requestBan(second.getUUID(), second.getName(), message, banEnd, adminBan)) {
                        result.addPlayerBan(second.getName(), message, banEnd);
                    }
                }
            } else {
                if (first.getId() == limitBanTo.getId()) {
                    PlayerStatus firstStatus = getPlayerStatus(first.getUUID());
                    if (firstStatus.sharedPardonTime() == null && !firstStatus.banned()
                        && requestBan(first.getUUID(), first.getName(), message, banEnd, adminBan)) {
                        result.addPlayerBan(first.getName(), message, banEnd);
                    }
                }
                if (second.getId() == limitBanTo.getId()) {
                    PlayerStatus secondStatus = getPlayerStatus(second.getUUID());
                    if (secondStatus.sharedPardonTime() == null && !secondStatus.banned()
                        && requestBan(second.getUUID(), second.getName(), message, banEnd, adminBan)) {
                        result.addPlayerBan(second.getName(), message, banEnd);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            BanStick.getPlugin().warning("Failed to issue Share ban: ", e);
            return new BanResult();
        }
    }

    /**
     * Result of {@link #doUnban(UUID)}.
     *
     * @param success   true if the request succeeded (whether or not they were
     *                  actually banned).
     * @param wasBanned true if the player actually had an active ban that was
     *                  cleared; false if they were already unbanned.
     */
    public record UnbanResult(boolean success, boolean wasBanned) {
    }

    /**
     * Asks banstick-velocity to clear the active ban for a specific UUID, if any.
     *
     * @param playerId The UUID of the player to unban.
     */
    public static UnbanResult doUnban(UUID playerId) {
        AckResponse response = redisClient().issueUnban(playerId);
        return new UnbanResult(response.success(), Boolean.TRUE.equals(response.wasBanned()));
    }

    /**
     * Checks whether a player is banned.
     *
     * @param player The player to check if banned.
     * @return Returns true if the player is banned.
     */
    public static boolean isPlayerBanned(final Player player) {
        return isPlayerBanned(player.getUniqueId());
    }

    /**
     * Checks whether a player is banned.
     *
     * @param puuid The player UUID to check if banned.
     * @return Returns true if the player is banned.
     */
    public static boolean isPlayerBanned(final UUID puuid) {
        BSBan ban = getActivePlayerBanOrTransitive(puuid);
        return ban != null;
    }

    /**
     * Checks whether a player is banned, and returns the ban.
     *
     * <p>Note: this reads Paper's locally-cached {@link BSPlayer}/{@link BSBan}
     * data (including for transitive alt checks), not banstick-velocity directly.
     * It's used for the local alt-share-kick heuristic in {@link com.programmerdan.minecraft.banstick.data.BSShares},
     * not for primary ban enforcement -- that now happens authoritatively at the
     * proxy login gate and via {@link #doUUIDBan} / {@link #doUnban}, which are
     * always consistent with banstick-velocity regardless of this server's cache
     * state.
     *
     * @param puuid The player UUID to check if banned.
     * @return Returns true if the player is banned.
     */
    public static BSBan getActivePlayerBanOrTransitive(final UUID puuid) {
        final BSPlayer bsPlayer = BSPlayer.byUUID(puuid);
        if (bsPlayer == null) {
            return null;
        }
        final BSBan bsBan = bsPlayer.getBan();
        if (bsBan != null && !bsBan.hasBanExpired()) {
            return bsBan;
        }
        if (BanStick.getPlugin().getEventHandler().areTransitiveBansEnabled()) {
            for (final BSPlayer alt : bsPlayer.getTransitiveSharedPlayers(true)) {
                final BSBan bsAltBan = alt.getBan();
                if (bsAltBan != null && !bsAltBan.hasBanExpired()) {
                    return bsAltBan;
                }
            }
        }
        return null;
    }
}
