package world.edenmc.banstickvelocity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;

/**
 * Owns all reads/writes against {@code bs_exclusion} -- the multi-account graph
 * exclusions used to keep specific players from being treated as alts during
 * transitive share lookups. Like {@link BanStore}, this is the only place that
 * touches this table, so there's no per-server cache to go stale between Main
 * and PVP.
 */
public class ExclusionStore {

    private final DataSource dataSource;
    private final Logger logger;

    public ExclusionStore(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    /**
     * All players a given player is excluded from. Empty list if the player is
     * unknown or has no exclusions.
     */
    public List<UUID> getExcludedPlayers(UUID uuid) {
        String sql = "SELECT p2.uuid FROM bs_exclusion e "
            + "JOIN bs_player p1 ON p1.pid = e.first_pid JOIN bs_player p2 ON p2.pid = e.second_pid "
            + "WHERE p1.uuid = ? "
            + "UNION "
            + "SELECT p1.uuid FROM bs_exclusion e "
            + "JOIN bs_player p1 ON p1.pid = e.first_pid JOIN bs_player p2 ON p2.pid = e.second_pid "
            + "WHERE p2.uuid = ?";
        List<UUID> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString(1)));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to look up exclusions for {}", uuid, e);
        }
        return result;
    }

    /**
     * Creates an exclusion between two known players. Idempotent -- returns true
     * without inserting a duplicate row if one already exists. Fails if either
     * player is unknown to banstick-velocity (no bs_player row).
     */
    public boolean createExclusion(UUID firstUuid, UUID secondUuid) {
        try (Connection conn = dataSource.getConnection()) {
            Long firstPid = resolvePid(conn, firstUuid);
            Long secondPid = resolvePid(conn, secondUuid);
            if (firstPid == null || secondPid == null) {
                logger.warn("Cannot create exclusion, unknown player(s): {} {}", firstUuid, secondUuid);
                return false;
            }
            if (findExclusionId(conn, firstPid, secondPid) != null) {
                return true; // already exists
            }
            try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO bs_exclusion(create_time, first_pid, second_pid) VALUES (CURRENT_TIMESTAMP, ?, ?)")) {
                insert.setLong(1, firstPid);
                insert.setLong(2, secondPid);
                insert.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            logger.error("Failed to create exclusion between {} and {}", firstUuid, secondUuid, e);
            return false;
        }
    }

    /**
     * Deletes the exclusion between two players, if any. Returns true whether or
     * not one existed -- "already not excluded" is not a failure.
     */
    public boolean deleteExclusion(UUID firstUuid, UUID secondUuid) {
        try (Connection conn = dataSource.getConnection()) {
            Long firstPid = resolvePid(conn, firstUuid);
            Long secondPid = resolvePid(conn, secondUuid);
            if (firstPid == null || secondPid == null) {
                return true;
            }
            Long eid = findExclusionId(conn, firstPid, secondPid);
            if (eid == null) {
                return true;
            }
            try (PreparedStatement delete = conn.prepareStatement("DELETE FROM bs_exclusion WHERE eid = ?")) {
                delete.setLong(1, eid);
                delete.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            logger.error("Failed to delete exclusion between {} and {}", firstUuid, secondUuid, e);
            return false;
        }
    }

    private Long findExclusionId(Connection conn, long firstPid, long secondPid) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement(
            "SELECT eid FROM bs_exclusion WHERE (first_pid = ? AND second_pid = ?) OR (first_pid = ? AND second_pid = ?)")) {
            check.setLong(1, firstPid);
            check.setLong(2, secondPid);
            check.setLong(3, secondPid);
            check.setLong(4, firstPid);
            try (ResultSet rs = check.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private Long resolvePid(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement select = conn.prepareStatement("SELECT pid FROM bs_player WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }
}
