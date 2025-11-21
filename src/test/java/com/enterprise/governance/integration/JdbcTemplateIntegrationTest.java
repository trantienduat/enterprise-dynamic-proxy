package com.enterprise.governance.integration;

import com.enterprise.governance.core.GovernanceEngine;
import com.enterprise.governance.core.SimpleRuleEngine;
import com.enterprise.governance.jdbc.JdbcGovernance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating that governance works with Spring's JdbcTemplate.
 * This proves that the library works at the lowest level (DataSource) and governs
 * all frameworks that use JDBC underneath.
 */
class JdbcTemplateIntegrationTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private final GovernanceEngine engine = new SimpleRuleEngine();

    @BeforeEach
    void setUp() throws SQLException {
        // Create in-memory H2 database with unique name per test
        String dbName = "templatedb" + System.nanoTime();
        connection = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1", "sa", "");
        
        // Create test table
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2), active BOOLEAN)");
            stmt.execute("INSERT INTO products VALUES (1, 'Laptop', 999.99, true)");
            stmt.execute("INSERT INTO products VALUES (2, 'Mouse', 29.99, true)");
            stmt.execute("INSERT INTO products VALUES (3, 'Keyboard', 79.99, false)");
        }
        
        // Create DataSource and wrap it with governance
        DataSource originalDataSource = new SingleConnectionDataSource(connection, false);
        DataSource governedDataSource = JdbcGovernance.wrapDataSource(originalDataSource, engine);
        
        // Create JdbcTemplate with governed DataSource
        jdbcTemplate = new JdbcTemplate(governedDataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS products");
            }
            connection.close();
        }
    }

    @Test
    void testJdbcTemplateQuery() {
        // Normal queries should work fine
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
            "SELECT * FROM products WHERE active = true"
        );
        
        assertEquals(2, products.size());
    }

    @Test
    void testJdbcTemplateQueryForObject() {
        // Query for single object
        String name = jdbcTemplate.queryForObject(
            "SELECT name FROM products WHERE id = ?",
            String.class,
            1
        );
        
        assertEquals("Laptop", name);
    }

    @Test
    void testJdbcTemplateUpdate() {
        // Update with WHERE clause should work
        int rowsUpdated = jdbcTemplate.update(
            "UPDATE products SET price = ? WHERE id = ?",
            899.99, 1
        );
        
        assertEquals(1, rowsUpdated);
    }

    @Test
    void testJdbcTemplateBlockDeleteWithoutWhere() {
        // DELETE without WHERE should be blocked
        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update("DELETE FROM products");
        });
        
        assertTrue(exception.getMessage().contains("Governance Blocked") 
                || exception.getCause().getMessage().contains("Governance Blocked"));
    }

    @Test
    void testJdbcTemplateBlockUpdateWithoutWhere() {
        // UPDATE without WHERE should be blocked
        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update("UPDATE products SET active = false");
        });
        
        assertTrue(exception.getMessage().contains("Governance Blocked") 
                || exception.getCause().getMessage().contains("Governance Blocked"));
    }

    @Test
    void testJdbcTemplateAllowDeleteWithWhere() {
        // DELETE with WHERE clause should work
        int rowsDeleted = jdbcTemplate.update(
            "DELETE FROM products WHERE id = ?",
            3
        );
        
        assertEquals(1, rowsDeleted);
    }

    @Test
    void testJdbcTemplateBlockDropTable() {
        // DROP TABLE should be blocked
        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.execute("DROP TABLE products");
        });
        
        assertTrue(exception.getMessage().contains("Governance Blocked") 
                || exception.getCause().getMessage().contains("Governance Blocked"));
    }

    @Test
    void testJdbcTemplateBatchUpdate() {
        // Batch updates with WHERE clause should work
        int[] rowsUpdated = jdbcTemplate.batchUpdate(
            "UPDATE products SET active = ? WHERE id = ?",
            List.of(
                new Object[]{false, 1},
                new Object[]{true, 2}
            )
        );
        
        assertEquals(2, rowsUpdated.length);
        assertEquals(1, rowsUpdated[0]);
        assertEquals(1, rowsUpdated[1]);
    }
}
