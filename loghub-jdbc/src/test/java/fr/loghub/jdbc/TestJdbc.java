package fr.loghub.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJdbc {

    @Test
    public void testDriver() throws SQLException, ClassNotFoundException {
        Class.forName("loghub.jdbc.LogHubDriver");
        Connection conn = DriverManager.getConnection("jdbc:loghub:test");
        assertNotNull(conn);
        assertTrue(conn instanceof LogHubConnection);
        
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (col) VALUES (?)");
        assertNotNull(ps);
        assertTrue(ps instanceof LogHubPreparedStatement);
        
        ps.setString(1, "val");
        int result = ps.executeUpdate();
        assertEquals(1, result);
        
        conn.close();
        assertTrue(conn.isClosed());
    }

    @Test
    public void testInvalidSql() throws SQLException {
        LogHubConnection conn = new LogHubConnection("jdbc:loghub:test", null);
        assertThrows(SQLException.class, () -> {
            conn.prepareStatement("SELECT * FROM table");
        });
    }

    @Test
    public void testParameterCountMismatch() throws SQLException {
        LogHubConnection conn = new LogHubConnection("jdbc:loghub:test", null);
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (col) VALUES (?)");
        // Don't set any parameter
        assertThrows(SQLException.class, ps::executeUpdate);
    }

    @Test
    public void testSetParametersInDisorder() throws SQLException {
        LogHubConnection conn = new LogHubConnection("jdbc:loghub:test", null);
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (c1, c2) VALUES (?, ?)");
        ps.setString(2, "val2");
        ps.setString(1, "val1");
        int result = ps.executeUpdate();
        assertEquals(1, result);
    }

    @Test
    public void testInvalidParameterIndex() throws SQLException {
        LogHubConnection conn = new LogHubConnection("jdbc:loghub:test", null);
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (col) VALUES (?)");
        assertThrows(SQLException.class, () -> ps.setString(0, "val"));
    }

    @Test
    public void testUnsetParameter() throws SQLException {
        LogHubConnection conn = new LogHubConnection("jdbc:loghub:test", null);
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (c1, c2) VALUES (?, ?)");
        ps.setString(2, "val2");
        // Parameter 1 is not set
        assertThrows(SQLException.class, ps::executeUpdate);
    }

    @Test
    public void testExplicitNullParameter() throws SQLException {
        LogHubConnection conn = new LogHubConnection("jdbc:loghub:test", null);
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (c1, c2) VALUES (?, ?)");
        ps.setNull(1, java.sql.Types.VARCHAR);
        ps.setString(2, "val2");
        int result = ps.executeUpdate();
        assertEquals(1, result);
    }
}
