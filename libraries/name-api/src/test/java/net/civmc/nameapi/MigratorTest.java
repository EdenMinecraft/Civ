package net.civmc.nameapi;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigratorTest {

    @Test
    void persistsAppliedMigrationId() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(mock(Statement.class));

        ResultSet emptyResult = mock(ResultSet.class);
        when(emptyResult.next()).thenReturn(false);

        PreparedStatement selectStatement = mock(PreparedStatement.class);
        when(selectStatement.executeQuery()).thenReturn(emptyResult);
        when(connection.prepareStatement("SELECT id FROM migrations WHERE namespace = ? FOR UPDATE"))
            .thenReturn(selectStatement);

        PreparedStatement replaceStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement("REPLACE INTO migrations (namespace, id) VALUES (?, ?)"))
            .thenReturn(replaceStatement);

        Migrator migrator = new Migrator();
        migrator.registerMigration("test", 0, "SELECT 1");
        migrator.migrate(connection);

        verify(replaceStatement).setInt(2, 0);
        verify(replaceStatement).executeUpdate();
    }
}
