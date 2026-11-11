package com.github.longboyy.eve.database;

import com.github.longboyy.eve.model.Relay;
import com.github.longboyy.eve.model.RelayType;
import com.untamedears.jukealert.JukeAlert;
import org.jetbrains.annotations.Nullable;
import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class EveDAO extends ManagedDAO {

    private static final String LOAD_ALL_RELAYS = "SELECT * FROM relays;";
    private static final String INSERT_NEW_RELAY = "INSERT INTO relays(group_id, guild_id, channel_id, relay_type) VALUES(?, ?, ?, ?);";
    private static final String REMOVE_RELAY = "DELETE FROM relays WHERE id = ?;";

    public EveDAO(ManagedDatasource db){
        super(db);
    }

    @Override
    public void registerMigrations() {
        this.db.registerMigration(1, false, """
            CREATE TABLE IF NOT EXISTS relays (
            id INT AUTO_INCREMENT NOT NULL,
            group_id INT NOT NULL,
            channel_id VARCHAR(20) NOT NULL,
            guild_id VARCHAR(20) NOT NULL,
            config_id INT DEFAULT -1,
            PRIMARY KEY (id));
            """);
        // Existing relays predate chat relays, so they are all snitch relays.
        this.db.registerMigration(2, false, """
            ALTER TABLE relays ADD COLUMN relay_type VARCHAR(16) NOT NULL DEFAULT 'SNITCH';
            """);
    }

    public @Nullable List<Relay> loadRelays(){
        return executeQuery(LOAD_ALL_RELAYS, rs -> new Relay(
            rs.getInt("group_id"),
            rs.getString("guild_id"),
            rs.getString("channel_id"),
            rs.getInt("id"),
            RelayType.fromString(rs.getString("relay_type"))
        ));
    }

    public @Nullable Relay insertRelay(int groupId, String guildId, String channelId, RelayType type){
        return executeInsert(INSERT_NEW_RELAY, stmt -> {
            stmt.setInt(1, groupId);
            stmt.setString(2, guildId);
            stmt.setString(3, channelId);
            stmt.setString(4, type.name());
        }, rs -> new Relay(groupId, guildId, channelId, rs.getInt(1), type));
    }

    public boolean removeRelay(int id){
        return executeUpdate(REMOVE_RELAY, stmt -> stmt.setInt(1, id));
    }

    private <T> @Nullable List<T> executeQuery(String sql, ResultSetMapper<T> mapper) {
        List<T> results = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapper.map(rs));
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Error executing query: " + sql, ex);
            return null;
        }
        return results;
    }

    private <T> @Nullable T executeInsert(String sql, PreparedStatementSetter setter, ResultSetMapper<T> keyMapper) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setter.set(ps);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keyMapper.map(keys);
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Error executing insert: " + sql, ex);
        }
        return null;
    }

    private boolean executeUpdate(String sql, PreparedStatementSetter setter) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.set(ps);
            ps.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Error executing update: " + sql, ex);
            return false;
        }
        return true;
    }

    @FunctionalInterface
    interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    interface PreparedStatementSetter {
        void set(PreparedStatement ps) throws SQLException;
    }

}
