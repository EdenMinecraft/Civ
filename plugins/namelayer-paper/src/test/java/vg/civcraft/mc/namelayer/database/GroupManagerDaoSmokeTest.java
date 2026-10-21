package vg.civcraft.mc.namelayer.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vg.civcraft.mc.namelayer.GroupManager.PlayerType;

/**
 * Real-MariaDB smoke test: drives createGroup + addMember through the DAO and reads the members back
 * out, proving the container booted, the migrations built the schema, and the stored procedures and
 * member queries actually run end to end.
 */
class GroupManagerDaoSmokeTest extends NameLayerDbTest {

    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private final UUID admin = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private final UUID mod = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private final UUID member = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    @Test
    void createGroupAddMembersReadBack() {
        int groupId = dao.createGroup("smoketown", owner, null);
        assertTrue(groupId > 0, "createGroup should return a positive generated id");

        dao.addMember(admin, "smoketown", PlayerType.ADMINS);
        dao.addMember(mod, "smoketown", PlayerType.MODS);
        dao.addMember(member, "smoketown", PlayerType.MEMBERS);

        Map<UUID, PlayerType> members = dao.getAllMembers("smoketown");

        Map<UUID, PlayerType> expected = Map.of(
            owner, PlayerType.OWNER,
            admin, PlayerType.ADMINS,
            mod, PlayerType.MODS,
            member, PlayerType.MEMBERS);
        assertEquals(expected, members);
    }

    @Test
    void getAllMembersByRoleReadsOnlyThatRole() {
        dao.createGroup("rolescope", owner, null);
        dao.addMember(admin, "rolescope", PlayerType.ADMINS);

        assertEquals(java.util.List.of(owner), dao.getAllMembers("rolescope", PlayerType.OWNER));
        assertEquals(java.util.List.of(admin), dao.getAllMembers("rolescope", PlayerType.ADMINS));
        assertEquals(java.util.List.of(), dao.getAllMembers("rolescope", PlayerType.MODS));
    }
}
