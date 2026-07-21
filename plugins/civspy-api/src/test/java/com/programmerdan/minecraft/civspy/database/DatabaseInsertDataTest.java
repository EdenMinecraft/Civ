package com.programmerdan.minecraft.civspy.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class DatabaseInsertDataTest {

    /**
     * INSERT_STRING binds (stat_time, stat_key, string_value, server, world, ...), so with a string value
     * present the server column is parameter 4 and the world column is parameter 5.
     */
    private static final int SERVER_INDEX = 4;
    private static final int WORLD_INDEX = 5;

    @Test
    public void convenienceOverloadBindsServerAndWorldToCorrectColumns() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        when(connection.prepareStatement(Database.INSERT_STRING)).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        Database db = new Database(Logger.getLogger("test"), null, null, null, 0, null, 0, 0L, 0L, 0L);

        db.insertData("test.key", "the-server", "the-world", 7, 9, "string-value", null, 123L, connection);

        ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).setString(indexCaptor.capture(), valueCaptor.capture());

        Map<Integer, String> boundStrings = new HashMap<>();
        List<Integer> indices = indexCaptor.getAllValues();
        List<String> values = valueCaptor.getAllValues();
        for (int i = 0; i < indices.size(); i++) {
            boundStrings.put(indices.get(i), values.get(i));
        }

        assertEquals("the-server", boundStrings.get(SERVER_INDEX), "server must bind to the server column");
        assertEquals("the-world", boundStrings.get(WORLD_INDEX), "world must bind to the world column");
    }
}
