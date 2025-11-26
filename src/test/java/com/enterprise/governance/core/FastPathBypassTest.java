package com.enterprise.governance.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FastPathBypassTest {
    
    @Test
    void testDefaultBypassPatterns() {
        FastPathBypass bypass = new FastPathBypass();
        
        // Standard health check queries
        assertTrue(bypass.shouldBypass("SELECT 1"));
        assertTrue(bypass.shouldBypass("select 1"));
        assertTrue(bypass.shouldBypass("SELECT 1 FROM DUAL"));
        assertTrue(bypass.shouldBypass("SELECT 1 AS ONE"));
        assertTrue(bypass.shouldBypass("SELECT CURRENT_TIMESTAMP"));
    }
    
    @Test
    void testHealthCheckVariations() {
        FastPathBypass bypass = new FastPathBypass();
        
        // With whitespace variations
        assertTrue(bypass.shouldBypass("  SELECT 1  "));
        assertTrue(bypass.shouldBypass("SELECT 1;"));
        assertTrue(bypass.shouldBypass("SELECT 1 ;"));
        
        // Numeric variations  
        assertTrue(bypass.shouldBypass("SELECT 2"));
        assertTrue(bypass.shouldBypass("SELECT 123"));
    }
    
    @Test
    void testMetadataQueries() {
        FastPathBypass bypass = new FastPathBypass();
        
        // INFORMATION_SCHEMA queries
        assertTrue(bypass.shouldBypass("SELECT * FROM INFORMATION_SCHEMA.TABLES"));
        assertTrue(bypass.shouldBypass("SELECT column_name FROM information_schema.columns WHERE table_name = 'users'"));
        
        // PostgreSQL system catalog
        assertTrue(bypass.shouldBypass("SELECT * FROM pg_catalog.pg_tables"));
        
        // MySQL SHOW commands
        assertTrue(bypass.shouldBypass("SHOW DATABASES"));
        assertTrue(bypass.shouldBypass("SHOW TABLES"));
        assertTrue(bypass.shouldBypass("SHOW COLUMNS FROM users"));
    }
    
    @Test
    void testVersionQueries() {
        FastPathBypass bypass = new FastPathBypass();
        
        assertTrue(bypass.shouldBypass("SELECT VERSION()"));
        assertTrue(bypass.shouldBypass("SELECT @@VERSION"));
    }
    
    @Test
    void testNormalQueriesNotBypassed() {
        FastPathBypass bypass = new FastPathBypass();
        
        // Regular queries should NOT bypass
        assertFalse(bypass.shouldBypass("SELECT * FROM users"));
        assertFalse(bypass.shouldBypass("DELETE FROM users"));
        assertFalse(bypass.shouldBypass("UPDATE users SET active = 1"));
        assertFalse(bypass.shouldBypass("DROP TABLE users"));
        assertFalse(bypass.shouldBypass("SELECT id, name FROM users WHERE id = 1"));
    }
    
    @Test
    void testDisabledBypass() {
        FastPathBypass bypass = new FastPathBypass(false, List.of());
        
        // Nothing should bypass when disabled
        assertFalse(bypass.shouldBypass("SELECT 1"));
        assertFalse(bypass.shouldBypass("SELECT 1 FROM DUAL"));
        assertFalse(bypass.isEnabled());
    }
    
    @Test
    void testCustomPatterns() {
        FastPathBypass bypass = new FastPathBypass(true, List.of(
            "^PING$",
            "^SELECT\\s+HEALTH\\s+FROM\\s+DUAL$"
        ));
        
        // Custom patterns should work
        assertTrue(bypass.shouldBypass("PING"));
        assertTrue(bypass.shouldBypass("SELECT HEALTH FROM DUAL"));
        
        // Default patterns should still work
        assertTrue(bypass.shouldBypass("SELECT 1"));
    }
    
    @Test
    void testNullAndEmptyHandling() {
        FastPathBypass bypass = new FastPathBypass();
        
        assertFalse(bypass.shouldBypass(null));
        assertFalse(bypass.shouldBypass(""));
        assertFalse(bypass.shouldBypass("   "));
    }
    
    @Test
    void testInvalidCustomPatterns() {
        // Should not throw exception for invalid patterns
        assertDoesNotThrow(() -> {
            FastPathBypass bypass = new FastPathBypass(true, List.of(
                "[invalid",  // Invalid regex
                "valid.*"    // Valid regex
            ));
            
            // Valid pattern should still work
            assertTrue(bypass.shouldBypass("SELECT 1"));
        });
    }
    
    @Test
    void testCounts() {
        FastPathBypass bypass = new FastPathBypass();
        
        assertTrue(bypass.getExactBypassCount() > 0);
        assertTrue(bypass.getPatternBypassCount() > 0);
        assertTrue(bypass.isEnabled());
    }
    
    @Test
    void testOracleSyntax() {
        FastPathBypass bypass = new FastPathBypass();
        
        assertTrue(bypass.shouldBypass("SELECT 1 FROM DUAL"));
        assertTrue(bypass.shouldBypass("SELECT 'X'"));
    }
    
    @Test
    void testDB2Syntax() {
        FastPathBypass bypass = new FastPathBypass();
        
        assertTrue(bypass.shouldBypass("VALUES 1"));
        assertTrue(bypass.shouldBypass("SELECT CURRENT TIMESTAMP FROM SYSIBM.SYSDUMMY1"));
    }
}
