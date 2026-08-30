package fr.loghub.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import fr.loghub.core.Factory;
import fr.loghub.core.publishers.Publisher;
import fr.loghub.core.serializers.Serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJdbc {

    /**
     * A dummy factory that provides a serializer and a publisher which simply consume
     * the data without doing anything, so the tests can exercise the JDBC layer.
     */
    private static Factory dummyFactory() {
        return new Factory() {
            @Override
            public Publisher getPublisher() {
                return new Publisher() {
                    @Override
                    public void close() {
                        // Do nothing
                    }

                    @Override
                    public boolean send(byte[] content) {
                        // Just consume the content
                        return true;
                    }
                };
            }

            @Override
            public Serializer getSerializer() {
                return new Serializer() {
                    @Override
                    public Optional<byte[]> serialize(Map<String, Object> map) {
                        // Just consume the values
                        return Optional.of(new byte[0]);
                    }
                };
            }
        };
    }

    @Test
    public void testDriver() throws SQLException, ClassNotFoundException {
        Class.forName("fr.loghub.jdbc.LogHubDriver");
        Connection conn = DriverManager.getConnection("jdbc:loghub:test");
        assertNotNull(conn);
        assertTrue(conn instanceof LogHubConnection);

        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (col) VALUES (?)");
        assertNotNull(ps);
        assertTrue(ps instanceof LogHubPreparedStatement);

        conn.close();
        assertTrue(conn.isClosed());

        // The execution flow is exercised through a connection backed by the dummy factory
        LogHubConnection dummyConn = new LogHubConnection(dummyFactory());
        PreparedStatement dummyPs = dummyConn.prepareStatement("INSERT INTO table (col) VALUES (?)");
        dummyPs.setString(1, "val");
        int result = dummyPs.executeUpdate();
        assertEquals(1, result);
    }

    @Test
    public void testInvalidSql() throws SQLException {
        LogHubConnection conn = new LogHubConnection(dummyFactory());
        assertThrows(SQLException.class, () -> {
            conn.prepareStatement("SELECT * FROM table");
        });
    }

    @Test
    public void testParameterCountMismatch() throws SQLException {
        LogHubConnection conn = new LogHubConnection(dummyFactory());
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (col) VALUES (?)");
        // Don't set any parameter
        assertThrows(SQLException.class, ps::executeUpdate);
    }

    @Test
    public void testSetParametersInDisorder() throws SQLException {
        LogHubConnection conn = new LogHubConnection(dummyFactory());
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (c1, c2) VALUES (?, ?)");
        ps.setString(2, "val2");
        ps.setString(1, "val1");
        int result = ps.executeUpdate();
        assertEquals(1, result);
    }

    @Test
    public void testInvalidParameterIndex() throws SQLException {
        LogHubConnection conn = new LogHubConnection(dummyFactory());
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (col) VALUES (?)");
        assertThrows(SQLException.class, () -> ps.setString(0, "val"));
    }

    @Test
    public void testUnsetParameter() throws SQLException {
        LogHubConnection conn = new LogHubConnection(dummyFactory());
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (c1, c2) VALUES (?, ?)");
        ps.setString(2, "val2");
        // Parameter 1 is not set
        assertThrows(SQLException.class, ps::executeUpdate);
    }

    @Test
    public void testExplicitNullParameter() throws SQLException {
        LogHubConnection conn = new LogHubConnection(dummyFactory());
        PreparedStatement ps = conn.prepareStatement("INSERT INTO table (c1, c2) VALUES (?, ?)");
        ps.setNull(1, java.sql.Types.VARCHAR);
        ps.setString(2, "val2");
        int result = ps.executeUpdate();
        assertEquals(1, result);
    }
}
