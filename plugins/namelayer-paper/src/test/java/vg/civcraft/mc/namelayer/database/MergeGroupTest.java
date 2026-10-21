package vg.civcraft.mc.namelayer.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vg.civcraft.mc.namelayer.GroupManager.PlayerType;

/**
 * Exercises the plain-JDBC {@link GroupManagerDao#mergeGroup(String, String)} against a real MariaDB.
 * Each group_name owns exactly one faction_id row, so merge folds the source's single group_id into
 * the destination's single group_id: members move, overlaps resolve to the destination's role,
 * subgroups follow, and the source group disappears entirely.
 */
class MergeGroupTest extends NameLayerDbTest {

    private final UUID destOwner = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    private final UUID srcOwner = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private final UUID srcMember = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private final UUID shared = UUID.fromString("00000000-0000-0000-0000-0000000000b3");

    @Test
    void disjointMembersAllMoveToDestination() {
        dao.createGroup("dest", destOwner, null);
        dao.createGroup("src", srcOwner, null);
        dao.addMember(srcMember, "src", PlayerType.MODS);

        dao.mergeGroup("dest", "src");

        Map<UUID, PlayerType> members = dao.getAllMembers("dest");
        assertEquals(
            Map.of(destOwner, PlayerType.OWNER, srcOwner, PlayerType.OWNER, srcMember, PlayerType.MODS),
            members);
    }

    @Test
    void overlappingMemberKeepsDestinationRole() throws SQLException {
        dao.createGroup("dest", destOwner, null);
        dao.createGroup("src", srcOwner, null);
        // Same player, different roles in each group. Destination has the player as ADMINS.
        dao.addMember(shared, "dest", PlayerType.ADMINS);
        dao.addMember(shared, "src", PlayerType.MEMBERS);

        dao.mergeGroup("dest", "src");

        Map<UUID, PlayerType> members = dao.getAllMembers("dest");
        assertEquals(PlayerType.ADMINS, members.get(shared), "destination role must win on overlap");

        int destId = groupId("dest");
        assertEquals(1, memberRowCount(destId, shared), "overlap must leave exactly one row for the shared member");
    }

    @Test
    void subgroupCarriesToDestination() throws SQLException {
        dao.createGroup("dest", destOwner, null);
        dao.createGroup("src", srcOwner, null);
        dao.createGroup("child", srcOwner, null);
        // child is a subgroup of src; after merge it must be a subgroup of dest.
        dao.addSubGroup("src", "child");

        int destId = groupId("dest");
        int srcId = groupId("src");
        int childId = groupId("child");
        assertTrue(subGroupLinkExists(srcId, childId), "src should own child before merge");

        dao.mergeGroup("dest", "src");

        assertTrue(subGroupLinkExists(destId, childId), "child must become a subgroup of dest");
        assertFalse(subGroupLinkExists(srcId, childId), "src's old subgroup link must be gone");
    }

    @Test
    void sourceGroupFullyRemoved() throws SQLException {
        dao.createGroup("dest", destOwner, null);
        dao.createGroup("src", srcOwner, null);
        dao.addMember(srcMember, "src", PlayerType.MODS);

        dao.mergeGroup("dest", "src");

        assertNull(dao.getGroup("src"), "source group should no longer load");
        assertFalse(factionRowExists("src"), "source faction header row should be gone");
        assertFalse(factionIdRowExists("src"), "source faction_id row should be gone");
        assertTrue(factionIdRowExists("dest"), "destination must survive the merge");
    }

    private boolean subGroupLinkExists(int superId, int subId) throws SQLException {
        try (Connection connection = datasource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select 1 from subgroup where group_id = ? and sub_group_id = ?")) {
            statement.setInt(1, superId);
            statement.setInt(2, subId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    private int groupId(String groupName) throws SQLException {
        try (Connection connection = datasource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select group_id from faction_id where group_name = ?")) {
            statement.setString(1, groupName);
            try (ResultSet set = statement.executeQuery()) {
                assertTrue(set.next(), "expected a faction_id row for " + groupName);
                return set.getInt(1);
            }
        }
    }

    private int memberRowCount(int groupId, UUID member) throws SQLException {
        try (Connection connection = datasource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select count(*) from faction_member where group_id = ? and member_name = ?")) {
            statement.setInt(1, groupId);
            statement.setString(2, member.toString());
            try (ResultSet set = statement.executeQuery()) {
                set.next();
                return set.getInt(1);
            }
        }
    }

    private boolean factionRowExists(String groupName) throws SQLException {
        return rowExists("select 1 from faction where group_name = ?", groupName);
    }

    private boolean factionIdRowExists(String groupName) throws SQLException {
        return rowExists("select 1 from faction_id where group_name = ?", groupName);
    }

    private boolean rowExists(String sql, String groupName) throws SQLException {
        try (Connection connection = datasource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, groupName);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }
}
