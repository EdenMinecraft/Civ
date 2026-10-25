package vg.civcraft.mc.namelayer.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;

/**
 * Proves migration 16 collapses the legacy shadow-id model down to one faction_id per group_name.
 *
 * <p>The default {@link NameLayerDbTest} harness can't show this: it runs every migration against an
 * empty schema (so the dedup touches nothing), and after migration 16 the UNIQUE(group_name)
 * constraint forbids ever creating two ids with the same name again. So this test builds a SECOND
 * database frozen at migration 15 (pre-collapse), seeds a group_name that owns TWO faction_id rows
 * with members / subgroups / perms / blacklist spread across both, then applies migration 16 alone
 * and asserts the two ids folded into one canonical id with everything preserved.
 *
 * <p>The two migration sets are lifted straight from the real {@link GroupManagerDao#registerMigrations()}
 * (read off a throwaway datasource via reflection) so the test exercises the exact production SQL, not a
 * hand-copied approximation.
 */
class CollapseShadowIdsTest extends NameLayerDbTest {

    private static final String GROUP = "collapseme";

    // Canonical id: the one with the most distinct members (ties broken by lowest id). We give CANON
    // strictly more members than SHADOW so the winner is deterministic and overlap roles come from it.
    private static int canonId;
    private static int shadowId;

    // Members. "overlap" sits in both ids with different roles; the canonical role must survive.
    private static final String OWNER = "00000000-0000-0000-0000-0000000000c0";
    private static final String CANON_A = "00000000-0000-0000-0000-0000000000c1";
    private static final String CANON_B = "00000000-0000-0000-0000-0000000000c2";
    private static final String SHADOW_ONLY = "00000000-0000-0000-0000-0000000000c3";
    private static final String OVERLAP = "00000000-0000-0000-0000-0000000000c4";

    // A separate group used as a subgroup so we can prove subgroup links repoint to the canonical id.
    private static final String CHILD = "child";
    private static int childId;

    private static ManagedDatasource v15;
    private static GroupManagerDao registry;

    @BeforeAll
    static void buildPreCollapseSchemaAndSeed() throws Exception {
        v15 = constructDatasource("namelayer_collapse");
        if (v15 == null) {
            throw new IllegalStateException("Could not construct the pre-collapse datasource");
        }

        // A throwaway dao registers the real migration set into its own datasource; we copy the
        // definitions out of it so we never duplicate the production SQL here.
        registry = new GroupManagerDao(Logger.getLogger("collapse-registry"), datasource);
        registry.registerMigrations();

        applyMigrationsUpTo(15);
        seedTwoIdsForOneGroup();
        applyMigration(16);
    }

    @AfterAll
    static void closePreCollapseDatasource() {
        if (v15 != null) {
            v15.close();
        }
    }

    @Test
    void exactlyOneFactionIdSurvivesForTheName() throws SQLException {
        List<Integer> ids = factionIdsFor(GROUP);
        assertEquals(List.of(canonId), ids,
            "collapse must leave exactly the canonical faction_id for the group_name");
        assertFalse(ids.contains(shadowId), "the shadow faction_id must be gone");
    }

    @Test
    void uniqueGroupNameConstraintIsNowEnforced() throws SQLException {
        assertTrue(hasUniqueIndexOnGroupName(),
            "migration 16 must add a UNIQUE key on faction_id.group_name");
    }

    @Test
    void allMembersLandOnCanonicalIdWithCanonicalRoleOnOverlap() throws SQLException {
        Map<String, String> members = membersOf(canonId);
        assertEquals(
            Map.of(
                OWNER, "OWNER",
                CANON_A, "ADMINS",
                CANON_B, "MODS",
                SHADOW_ONLY, "MEMBERS",
                OVERLAP, "ADMINS"),
            members,
            "every member from both ids must sit under the canonical id, destination role winning on overlap");

        assertEquals(1, memberRowCount(canonId, OVERLAP),
            "overlap must collapse to a single row under the canonical id");
        assertEquals(0, totalMemberRowsFor(shadowId), "no member rows may remain on the shadow id");
    }

    @Test
    void subgroupLinkRepointsToCanonicalId() throws SQLException {
        assertTrue(subGroupLinkExists(canonId, childId),
            "the child subgroup link must follow to the canonical id");
        assertFalse(subGroupLinkExists(shadowId, childId),
            "the shadow id's subgroup link must be gone");
    }

    @Test
    void permissionsMergeUnderCanonicalIdWithoutDuplicates() throws SQLException {
        Set<String> perms = permRolePerm(canonId);
        // CANON had (OWNER,1); SHADOW had (OWNER,1) [dup, dropped] and (MODS,2) [moves over].
        assertEquals(Set.of("OWNER:1", "MODS:2"), perms,
            "perms from both ids must land on the canonical id, conflicting rows deduped");
        assertEquals(0, permRowCount(shadowId), "no permissionByGroup rows may remain on the shadow id");
    }

    @Test
    void blacklistEntriesMoveUnderCanonicalIdWithoutDuplicates() throws SQLException {
        Set<String> entries = blacklistMembers(canonId);
        // CANON blacklisted OVERLAP; SHADOW blacklisted OVERLAP [dup] and SHADOW_ONLY.
        assertEquals(Set.of(OVERLAP, SHADOW_ONLY), entries,
            "blacklist entries from both ids must land on the canonical id, duplicates dropped");
        assertEquals(0, blacklistRowCount(shadowId), "no blacklist rows may remain on the shadow id");
    }

    // --- seeding -------------------------------------------------------------------------------

    private static void seedTwoIdsForOneGroup() throws SQLException {
        try (Connection c = v15.getConnection()) {
            // Two faction_id rows sharing one name: the legacy shadow-id state migration 16 must fix.
            canonId = insertFactionId(c, GROUP);
            shadowId = insertFactionId(c, GROUP);
            childId = insertFactionId(c, CHILD);

            // faction headers (one per name; the canonical id wears the name).
            insertFaction(c, GROUP);
            insertFaction(c, CHILD);

            // CANON has 4 distinct members, SHADOW has 2 -> CANON is canonical (most members).
            addMember(c, canonId, OWNER, "OWNER");
            addMember(c, canonId, CANON_A, "ADMINS");
            addMember(c, canonId, CANON_B, "MODS");
            addMember(c, canonId, OVERLAP, "ADMINS");

            addMember(c, shadowId, SHADOW_ONLY, "MEMBERS");
            addMember(c, shadowId, OVERLAP, "MEMBERS"); // overlap, lower role: must lose to CANON's ADMINS

            // subgroup link lives on the shadow id; must repoint to the canonical id.
            addSubgroup(c, shadowId, childId);

            // perms: a duplicate (OWNER) plus a shadow-only (MODS) to prove dedup + move.
            addPerm(c, canonId, "OWNER", 1);
            addPerm(c, shadowId, "OWNER", 1);
            addPerm(c, shadowId, "MODS", 2);

            // blacklist: a duplicate plus a shadow-only entry.
            addBlacklist(c, canonId, OVERLAP);
            addBlacklist(c, shadowId, OVERLAP);
            addBlacklist(c, shadowId, SHADOW_ONLY);
        }
    }

    private static int insertFactionId(Connection c, String name) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                 "insert into faction_id (group_name) values (?)", Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, name);
            s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static void insertFaction(Connection c, String name) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                 "insert into faction (group_name, founder, discipline_flags) values (?, ?, 0)")) {
            s.setString(1, name);
            s.setString(2, OWNER);
            s.executeUpdate();
        }
    }

    private static void addMember(Connection c, int groupId, String member, String role) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                 "insert into faction_member (group_id, member_name, role) values (?, ?, ?)")) {
            s.setInt(1, groupId);
            s.setString(2, member);
            s.setString(3, role);
            s.executeUpdate();
        }
    }

    private static void addSubgroup(Connection c, int groupId, int subId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                 "insert into subgroup (group_id, sub_group_id) values (?, ?)")) {
            s.setInt(1, groupId);
            s.setInt(2, subId);
            s.executeUpdate();
        }
    }

    private static void addPerm(Connection c, int groupId, String role, int permId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                 "insert into permissionByGroup (group_id, role, perm_id) values (?, ?, ?)")) {
            s.setInt(1, groupId);
            s.setString(2, role);
            s.setInt(3, permId);
            s.executeUpdate();
        }
    }

    private static void addBlacklist(Connection c, int groupId, String member) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                 "insert into blacklist (member_name, group_id) values (?, ?)")) {
            s.setString(1, member);
            s.setInt(2, groupId);
            s.executeUpdate();
        }
    }

    // --- assertions ----------------------------------------------------------------------------

    private List<Integer> factionIdsFor(String name) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Connection c = v15.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "select group_id from faction_id where group_name = ? order by group_id")) {
            s.setString(1, name);
            try (ResultSet set = s.executeQuery()) {
                while (set.next()) {
                    ids.add(set.getInt(1));
                }
            }
        }
        return ids;
    }

    private Map<String, String> membersOf(int groupId) throws SQLException {
        Map<String, String> members = new java.util.HashMap<>();
        try (Connection c = v15.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "select member_name, role from faction_member where group_id = ?")) {
            s.setInt(1, groupId);
            try (ResultSet set = s.executeQuery()) {
                while (set.next()) {
                    members.put(set.getString(1), set.getString(2));
                }
            }
        }
        return members;
    }

    private int memberRowCount(int groupId, String member) throws SQLException {
        return scalar("select count(*) from faction_member where group_id = ? and member_name = ?",
            groupId, member);
    }

    private int totalMemberRowsFor(int groupId) throws SQLException {
        return scalar("select count(*) from faction_member where group_id = ?", groupId, null);
    }

    private boolean subGroupLinkExists(int superId, int subId) throws SQLException {
        try (Connection c = v15.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "select 1 from subgroup where group_id = ? and sub_group_id = ?")) {
            s.setInt(1, superId);
            s.setInt(2, subId);
            try (ResultSet set = s.executeQuery()) {
                return set.next();
            }
        }
    }

    private Set<String> permRolePerm(int groupId) throws SQLException {
        Set<String> out = new HashSet<>();
        try (Connection c = v15.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "select role, perm_id from permissionByGroup where group_id = ?")) {
            s.setInt(1, groupId);
            try (ResultSet set = s.executeQuery()) {
                while (set.next()) {
                    out.add(set.getString(1) + ":" + set.getInt(2));
                }
            }
        }
        return out;
    }

    private int permRowCount(int groupId) throws SQLException {
        return scalar("select count(*) from permissionByGroup where group_id = ?", groupId, null);
    }

    private Set<String> blacklistMembers(int groupId) throws SQLException {
        Set<String> out = new HashSet<>();
        try (Connection c = v15.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "select member_name from blacklist where group_id = ?")) {
            s.setInt(1, groupId);
            try (ResultSet set = s.executeQuery()) {
                while (set.next()) {
                    out.add(set.getString(1));
                }
            }
        }
        return out;
    }

    private int blacklistRowCount(int groupId) throws SQLException {
        return scalar("select count(*) from blacklist where group_id = ?", groupId, null);
    }

    private boolean hasUniqueIndexOnGroupName() throws SQLException {
        try (Connection c = v15.getConnection();
             Statement s = c.createStatement();
             ResultSet set = s.executeQuery("show index from faction_id where Column_name = 'group_name'")) {
            while (set.next()) {
                if (set.getInt("Non_unique") == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private int scalar(String sql, int groupId, String member) throws SQLException {
        try (Connection c = v15.getConnection();
             PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1, groupId);
            if (member != null) {
                s.setString(2, member);
            }
            try (ResultSet set = s.executeQuery()) {
                set.next();
                return set.getInt(1);
            }
        }
    }

    // --- migration plumbing --------------------------------------------------------------------

    private static void applyMigrationsUpTo(int maxId) throws Exception {
        for (int id = 1; id <= maxId; id++) {
            registerMigrationOnV15(id);
        }
        if (!v15.updateDatabase()) {
            throw new IllegalStateException("Failed to build the pre-collapse (v15) schema");
        }
    }

    private static void applyMigration(int id) throws Exception {
        registerMigrationOnV15(id);
        if (!v15.updateDatabase()) {
            throw new IllegalStateException("Failed to apply migration " + id);
        }
    }

    /**
     * Copies migration {@code id} from the throwaway registry's private migration map onto the v15
     * datasource, so the exact production migration (queries + any post-callback) is what runs here.
     */
    @SuppressWarnings("unchecked")
    private static void registerMigrationOnV15(int id) throws Exception {
        java.lang.reflect.Field migrationsField = ManagedDatasource.class.getDeclaredField("migrations");
        migrationsField.setAccessible(true);

        NavigableMap<Integer, Object> registered =
            (NavigableMap<Integer, Object>) migrationsField.get(registrySource());
        Object migration = registered.get(id);
        if (migration == null) {
            return; // gap in the migration numbering; nothing to apply at this id.
        }

        Class<?> migrationClass = migration.getClass();
        boolean ignoreErrors = (boolean) accessor(migrationClass, "ignoreErrors").invoke(migration);
        Callable<Boolean> post = (Callable<Boolean>) accessor(migrationClass, "postMigration").invoke(migration);
        List<String> queries = (List<String>) accessor(migrationClass, "migrations").invoke(migration);

        v15.registerMigration(id, ignoreErrors, post, queries.toArray(new String[0]));
    }

    private static ManagedDatasource registrySource() throws Exception {
        java.lang.reflect.Field db = GroupManagerDao.class.getDeclaredField("db");
        db.setAccessible(true);
        return (ManagedDatasource) db.get(registry);
    }

    private static Method accessor(Class<?> recordClass, String component) throws Exception {
        Method m = recordClass.getDeclaredMethod(component);
        m.setAccessible(true);
        return m;
    }
}
