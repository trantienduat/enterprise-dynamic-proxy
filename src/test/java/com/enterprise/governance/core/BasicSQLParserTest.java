package com.enterprise.governance.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BasicSQLParserTest {

    private final SQLParser parser = new BasicSQLParser();

    @Test
    void testParseSelectQuery() {
        SQLParser.ParsedSQL parsed = parser.parse("SELECT id, name FROM users WHERE id = 1");
        
        assertEquals(SQLParser.SQLType.SELECT, parsed.getType());
        assertEquals("users", parsed.getTableName());
        assertTrue(parsed.hasWhereClause());
        assertFalse(parsed.hasSelectAll());
    }

    @Test
    void testParseSelectAll() {
        SQLParser.ParsedSQL parsed = parser.parse("SELECT * FROM products");
        
        assertEquals(SQLParser.SQLType.SELECT, parsed.getType());
        assertEquals("products", parsed.getTableName());
        assertFalse(parsed.hasWhereClause());
        assertTrue(parsed.hasSelectAll());
    }

    @Test
    void testParseUpdateWithWhere() {
        SQLParser.ParsedSQL parsed = parser.parse("UPDATE users SET active = 1 WHERE id = 5");
        
        assertEquals(SQLParser.SQLType.UPDATE, parsed.getType());
        assertEquals("users", parsed.getTableName());
        assertTrue(parsed.hasWhereClause());
    }

    @Test
    void testParseUpdateWithoutWhere() {
        SQLParser.ParsedSQL parsed = parser.parse("UPDATE users SET active = 1");
        
        assertEquals(SQLParser.SQLType.UPDATE, parsed.getType());
        assertEquals("users", parsed.getTableName());
        assertFalse(parsed.hasWhereClause());
    }

    @Test
    void testParseDeleteWithWhere() {
        SQLParser.ParsedSQL parsed = parser.parse("DELETE FROM orders WHERE status = 'cancelled'");
        
        assertEquals(SQLParser.SQLType.DELETE, parsed.getType());
        assertEquals("orders", parsed.getTableName());
        assertTrue(parsed.hasWhereClause());
    }

    @Test
    void testParseDeleteWithoutWhere() {
        SQLParser.ParsedSQL parsed = parser.parse("DELETE FROM orders");
        
        assertEquals(SQLParser.SQLType.DELETE, parsed.getType());
        assertEquals("orders", parsed.getTableName());
        assertFalse(parsed.hasWhereClause());
    }

    @Test
    void testParseInsert() {
        SQLParser.ParsedSQL parsed = parser.parse("INSERT INTO users (id, name) VALUES (1, 'Alice')");
        
        assertEquals(SQLParser.SQLType.INSERT, parsed.getType());
        assertEquals("users", parsed.getTableName());
    }

    @Test
    void testParseDropTable() {
        SQLParser.ParsedSQL parsed = parser.parse("DROP TABLE users");
        
        assertEquals(SQLParser.SQLType.DROP, parsed.getType());
        assertEquals("users", parsed.getTableName());
    }

    @Test
    void testParseTruncate() {
        SQLParser.ParsedSQL parsed = parser.parse("TRUNCATE TABLE logs");
        
        assertEquals(SQLParser.SQLType.TRUNCATE, parsed.getType());
        assertEquals("logs", parsed.getTableName());
    }

    @Test
    void testParseCreateTable() {
        SQLParser.ParsedSQL parsed = parser.parse("CREATE TABLE products (id INT, name VARCHAR(100))");
        
        assertEquals(SQLParser.SQLType.CREATE, parsed.getType());
        assertEquals("products", parsed.getTableName());
    }

    @Test
    void testParseAlterTable() {
        SQLParser.ParsedSQL parsed = parser.parse("ALTER TABLE users ADD COLUMN email VARCHAR(255)");
        
        assertEquals(SQLParser.SQLType.ALTER, parsed.getType());
        assertEquals("users", parsed.getTableName());
    }

    @Test
    void testParseNullSQL() {
        SQLParser.ParsedSQL parsed = parser.parse(null);
        
        assertEquals(SQLParser.SQLType.UNKNOWN, parsed.getType());
        assertNull(parsed.getTableName());
        assertFalse(parsed.hasWhereClause());
    }

    @Test
    void testParseEmptySQL() {
        SQLParser.ParsedSQL parsed = parser.parse("   ");
        
        assertEquals(SQLParser.SQLType.UNKNOWN, parsed.getType());
    }

    @Test
    void testParseCaseInsensitive() {
        SQLParser.ParsedSQL parsed = parser.parse("select * from USERS where ID = 1");
        
        assertEquals(SQLParser.SQLType.SELECT, parsed.getType());
        assertEquals("USERS", parsed.getTableName());
        assertTrue(parsed.hasWhereClause());
        assertTrue(parsed.hasSelectAll());
    }
}
