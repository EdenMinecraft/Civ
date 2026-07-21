package vg.civcraft.mc.namelayer.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;

/**
 * Regression tests for GroupManagerDao.getDefaultGroup. The empty-result case used to read the
 * ResultSet without calling next(); it must guard with next() and return null when there is no row.
 */
public class GetDefaultGroupTest {

    private final UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    private ManagedDatasource db;
    private ResultSet resultSet;
    private GroupManagerDao dao;

    @BeforeEach
    public void setUp() throws Exception {
        db = mock(ManagedDatasource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);

        when(db.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        dao = new GroupManagerDao(Logger.getLogger("test"), db);
    }

    @Test
    public void emptyResultReturnsNull() throws Exception {
        when(resultSet.next()).thenReturn(false);
        assertNull(dao.getDefaultGroup(uuid));
    }

    @Test
    public void presentRowReturnsGroupName() throws Exception {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(anyInt())).thenReturn("somegroup");
        assertEquals("somegroup", dao.getDefaultGroup(uuid));
    }
}
