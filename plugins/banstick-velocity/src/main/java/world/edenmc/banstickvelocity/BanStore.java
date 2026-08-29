package world.edenmc.banstickvelocity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;

/**
 * Owns all reads/writes against the direct-ban columns of the shared `banstick`
 * database: {@code bs_player.bid}, {@code bs_ban}, and {@code bs_ban_log}. This is
 * the only place in the whole system that touches those columns -- banstick-paper
 * asks for data/issues actions through banstick-velocity instead of writing them
 * itself, which is what keeps Main and PVP's view of ban state from ever diverging.
 *
 * <p>Schema creation/migration is owned by banstick-paper (see
 * BanStickDatabaseHandler#initializeTables); this class assumes the tables already
 * exist.
 */
public class BanStore {

    private final DataSource dataSource;
    private final Logger logger;

    public BanStore(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public record BanStatus(long banId, String message, Instant banEnd, boolean adminBan) {

        public boolean isExpired() {
            return banEnd != null && banEnd.isBefore(Instant.now());
        }
    }

    /**
     * Full ban + pardon status for a player. banstick-velocity is the sole owner
     * of all of these columns (bs_player.bid/ip_pardon_time/proxy_pardon_time/
     * shared_pardon_time and bs_ban), so this is always a fresh DB read -- there's
     * no local cache on this side to go stale.
     */
    public record PlayerStatus(Optional<BanStatus> ban, Instant ipPardon, Instant proxyPardon, Instant sharedPardon) {

        public static final PlayerStatus UNKNOWN = new PlayerStatus(Optional.empty(), null, null, null);
    }

    /**
     * Looks up the active ban and pardon timestamps for a player, straight from
     * the database. An unknown UUID (no bs_player row yet) returns
     * {@link PlayerStatus#UNKNOWN}.
     */
    public PlayerStatus getPlayerStatus(UUID uuid) {
        String sql = "SELECT b.bid, b.message, b.ban_end, b.admin_ban, "
            + "p.ip_pardon_time, p.proxy_pardon_time, p.shared_pardon_time "
            + "FROM bs_player p LEFT JOIN bs_ban b ON p.bid = b.bid WHERE p.uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return PlayerStatus.UNKNOWN;
                }
                Optional<BanStatus> ban = Optional.empty();
                long bid = rs.getLong(1);
                if (!rs.wasNull()) {
                    Timestamp banEndTs = rs.getTimestamp(3);
                    BanStatus status = new BanStatus(bid, rs.getString(2),
                        banEndTs != null ? banEndTs.toInstant() : null, rs.getBoolean(4));
                    if (!status.isExpired()) {
                        ban = Optional.of(status);
                    }
                }
                return new PlayerStatus(ban, toInstant(rs.getTimestamp(5)),
                    toInstant(rs.getTimestamp(6)), toInstant(rs.getTimestamp(7)));
            }
        } catch (SQLException e) {
            logger.error("Failed to look up player status for {}", uuid, e);
            return PlayerStatus.UNKNOWN;
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    /**
     * Sets or clears one of a player's pardon timestamps. Creates the player's
     * bs_player row if this is the first time banstick-velocity has seen this
     * UUID.
     *
     * @param pardonType one of {@link world.edenmc.banstickapi.PardonType}.
     * @param pardonTime the new pardon time, or null to clear the pardon.
     */
    public boolean setPardon(UUID uuid, String bestKnownName, String pardonType, Instant pardonTime) {
        String column = pardonColumn(pardonType);
        if (column == null) {
            logger.warn("Unknown pardon type: {}", pardonType);
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            long pid = getOrCreatePlayerId(conn, uuid, bestKnownName);
            try (PreparedStatement update =
                     conn.prepareStatement("UPDATE bs_player SET " + column + " = ? WHERE pid = ?")) {
                if (pardonTime != null) {
                    update.setTimestamp(1, Timestamp.from(pardonTime));
                } else {
                    update.setNull(1, Types.TIMESTAMP);
                }
                update.setLong(2, pid);
                update.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            logger.error("Failed to set {} pardon for {}", pardonType, uuid, e);
            return false;
        }
    }

    private static String pardonColumn(String pardonType) {
        return switch (pardonType) {
            case "IP" -> "ip_pardon_time";
            case "PROXY" -> "proxy_pardon_time";
            case "SHARED" -> "shared_pardon_time";
            default -> null;
        };
    }

    private long getOrCreatePlayerId(Connection conn, UUID uuid, String bestKnownName) throws SQLException {
        try (PreparedStatement select = conn.prepareStatement("SELECT pid FROM bs_player WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        try (PreparedStatement insert = conn.prepareStatement(
            "INSERT INTO bs_player(name, uuid, first_add) VALUES (?, ?, CURRENT_TIMESTAMP)",
            Statement.RETURN_GENERATED_KEYS)) {
            if (bestKnownName != null) {
                insert.setString(1, bestKnownName);
            } else {
                insert.setNull(1, Types.VARCHAR);
            }
            insert.setString(2, uuid.toString());
            insert.execute();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create bs_player row for " + uuid);
    }

    /**
     * Creates a new ban, points {@code bs_player.bid} at it, and records the
     * action in {@code bs_ban_log}. Creates the player's {@code bs_player} row if
     * this is the first time banstick-velocity has seen this UUID.
     */
    public boolean issueBan(UUID uuid, String bestKnownName, String message, Instant banEnd, boolean adminBan) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long pid = getOrCreatePlayerId(conn, uuid, bestKnownName);
                long bid = insertBan(conn, message, banEnd, adminBan);

                try (PreparedStatement updatePlayer =
                         conn.prepareStatement("UPDATE bs_player SET bid = ? WHERE pid = ?")) {
                    updatePlayer.setLong(1, bid);
                    updatePlayer.setLong(2, pid);
                    updatePlayer.executeUpdate();
                }

                logAction(conn, pid, bid, "BAN");
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Failed to issue ban for {}", uuid, e);
            return false;
        }
    }

    private long insertBan(Connection conn, String message, Instant banEnd, boolean adminBan) throws SQLException {
        try (PreparedStatement insertBan = conn.prepareStatement(
            "INSERT INTO bs_ban(ban_time, message, ban_end, admin_ban) VALUES (CURRENT_TIMESTAMP, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
            if (message != null) {
                insertBan.setString(1, message);
            } else {
                insertBan.setNull(1, Types.VARCHAR);
            }
            if (banEnd != null) {
                insertBan.setTimestamp(2, Timestamp.from(banEnd));
            } else {
                insertBan.setNull(2, Types.TIMESTAMP);
            }
            insertBan.setBoolean(3, adminBan);
            insertBan.execute();
            try (ResultSet keys = insertBan.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No bid returned on ban insert");
                }
                return keys.getLong(1);
            }
        }
    }

    /**
     * Result of {@link #clearBan(UUID)}.
     *
     * @param success   false only on a genuine failure (DB error, etc).
     * @param wasBanned true if the player actually had an active ban that was
     *                  cleared; false if they were already unbanned (still a
     *                  successful no-op, not a failure).
     */
    public record ClearBanResult(boolean success, boolean wasBanned) {

        public static final ClearBanResult FAILURE = new ClearBanResult(false, false);
    }

    /**
     * Clears the active ban for a player (if any) and records the action in
     * {@code bs_ban_log}. Succeeds whether or not the player was actually
     * banned -- "already unbanned" is not a failure, see {@link ClearBanResult#wasBanned()}.
     */
    public ClearBanResult clearBan(UUID uuid) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long pid;
                Long bid;
                try (PreparedStatement select =
                         conn.prepareStatement("SELECT pid, bid FROM bs_player WHERE uuid = ?")) {
                    select.setString(1, uuid.toString());
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return new ClearBanResult(true, false);
                        }
                        pid = rs.getLong(1);
                        long rawBid = rs.getLong(2);
                        bid = rs.wasNull() ? null : rawBid;
                    }
                }

                if (bid == null) {
                    conn.rollback();
                    return new ClearBanResult(true, false);
                }

                try (PreparedStatement clear =
                         conn.prepareStatement("UPDATE bs_player SET bid = NULL WHERE pid = ?")) {
                    clear.setLong(1, pid);
                    clear.executeUpdate();
                }

                logAction(conn, pid, bid, "UNBAN");
                conn.commit();
                return new ClearBanResult(true, true);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Failed to clear ban for {}", uuid, e);
            return ClearBanResult.FAILURE;
        }
    }

    private void logAction(Connection conn, long pid, long bid, String action) throws SQLException {
        try (PreparedStatement log =
                 conn.prepareStatement("INSERT INTO bs_ban_log (pid, bid, action) VALUES (?, ?, ?)")) {
            log.setLong(1, pid);
            log.setLong(2, bid);
            log.setString(3, action);
            log.execute();
        }
    }
}
