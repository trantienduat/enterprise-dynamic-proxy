package com.enterprise.governance.jdbc;

import com.enterprise.governance.core.GovernanceEngine;
import com.enterprise.governance.core.SimpleRuleEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;

import static org.junit.jupiter.api.Assertions.*;

class JdbcGovernanceTest {

    private Connection connection;
    private final GovernanceEngine engine = new SimpleRuleEngine();

    @BeforeEach
    void setUp() throws SQLException {
        // Create in-memory H2 database
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");
        
        // Create test table
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), active BOOLEAN)");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', true)");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', true)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS users");
            }
            connection.close();
        }
    }

    @Test
    void testAllowNormalQuery() throws SQLException {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        try (Statement stmt = govConnection.createStatement()) {
            var rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1");
            assertTrue(rs.next());
            assertEquals("Alice", rs.getString("name"));
        }
    }

    @Test
    void testBlockDeleteWithoutWhere() {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Statement stmt = govConnection.createStatement()) {
                stmt.execute("DELETE FROM users");
            }
        });
        
        assertTrue(exception.getMessage().contains("Governance Blocked"));
        assertTrue(exception.getMessage().contains("WHERE"));
    }

    @Test
    void testBlockUpdateWithoutWhere() {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Statement stmt = govConnection.createStatement()) {
                stmt.execute("UPDATE users SET active = false");
            }
        });
        
        assertTrue(exception.getMessage().contains("Governance Blocked"));
        assertTrue(exception.getMessage().contains("WHERE"));
    }

    @Test
    void testAllowUpdateWithWhere() throws SQLException {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        try (Statement stmt = govConnection.createStatement()) {
            int rows = stmt.executeUpdate("UPDATE users SET active = false WHERE id = 1");
            assertEquals(1, rows);
        }
    }

    @Test
    void testBlockDropTable() {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Statement stmt = govConnection.createStatement()) {
                stmt.execute("DROP TABLE users");
            }
        });
        
        assertTrue(exception.getMessage().contains("Governance Blocked"));
        assertTrue(exception.getMessage().contains("modification"));
    }

    @Test
    void testPreparedStatementWithParameters() throws SQLException {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        try (var pstmt = govConnection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            pstmt.setInt(1, 2);
            var rs = pstmt.executeQuery();
            assertTrue(rs.next());
            assertEquals("Bob", rs.getString("name"));
        }
    }

    @Test
    void testPreparedStatementBlockedQuery() {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (var pstmt = govConnection.prepareStatement("DELETE FROM users")) {
                pstmt.execute();
            }
        });
        
        assertTrue(exception.getMessage().contains("Governance Blocked"));
    }
    
    @Test
    void testUnwrapToConnection() throws SQLException {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        // Should be able to unwrap to Connection interface
        Connection unwrapped = govConnection.unwrap(Connection.class);
        assertNotNull(unwrapped);
        
        // Unwrapped connection should work
        assertFalse(unwrapped.isClosed());
    }
    
    @Test
    void testIsWrapperFor() throws SQLException {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        // Should indicate it wraps Connection
        assertTrue(govConnection.isWrapperFor(Connection.class));
        
        // Should not wrap unrelated interfaces
        assertFalse(govConnection.isWrapperFor(DataSource.class));
    }
    
    @Test
    void testUnwrapFailsForUnsupportedInterface() {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        // Should throw SQLException when unwrapping to unsupported interface
        assertThrows(SQLException.class, () -> {
            govConnection.unwrap(DataSource.class);
        });
    }
    
    @Test
    void testHealthCheckQueryBypassesGovernance() throws SQLException {
        Connection govConnection = JdbcGovernance.createProxy(connection, Connection.class, engine);
        
        try (Statement stmt = govConnection.createStatement()) {
            // SELECT 1 should bypass governance (fast-path)
            var rs = stmt.executeQuery("SELECT 1");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }
}
