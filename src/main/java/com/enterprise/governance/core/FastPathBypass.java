package com.enterprise.governance.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Fast-path bypass mechanism for health check and validation queries.
 * 
 * This class provides a performance optimization by allowing certain trusted queries
 * to bypass the governance parsing and rule evaluation. This is particularly important
 * for:
 * - Spring Actuator health check queries (DataSourceHealthIndicator)
 * - Connection pool validation queries (HikariCP, Tomcat, etc.)
 * - Database metadata retrieval operations
 * 
 * <h2>Default Bypass Patterns</h2>
 * The following patterns are bypassed by default:
 * <ul>
 *   <li>SELECT 1 - Standard validation query</li>
 *   <li>SELECT 1 FROM DUAL - Oracle validation query</li>
 *   <li>SELECT version() - PostgreSQL version check</li>
 *   <li>SHOW DATABASES - MySQL metadata</li>
 *   <li>Database metadata queries (INFORMATION_SCHEMA, etc.)</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * This class is thread-safe. The bypass patterns are immutable after construction,
 * and all operations are read-only.
 * 
 * <h2>Performance Considerations</h2>
 * Bypass checks use a combination of exact match (HashSet) and regex patterns
 * for optimal performance:
 * <ul>
 *   <li>Exact matches: O(1) lookup</li>
 *   <li>Regex patterns: Applied only if exact match fails</li>
 * </ul>
 */
public class FastPathBypass {
    
    /**
     * Default exact-match queries that bypass governance.
     * These are common health check and validation queries.
     */
    private static final Set<String> DEFAULT_EXACT_BYPASSES = Set.of(
        "SELECT 1",
        "SELECT 1 FROM DUAL",
        "SELECT 1 AS ONE",
        "VALUES 1",                          // DB2 validation
        "SELECT CURRENT_TIMESTAMP",          // PostgreSQL/MySQL
        "SELECT CURRENT TIMESTAMP FROM SYSIBM.SYSDUMMY1", // DB2
        "SELECT 'X'",                        // Generic validation
        "SELECT 'HELLO'",                    // Oracle validation alternative
        "SELECT CURRENT_DATE"                // Date validation
    );
    
    /**
     * Default regex patterns that bypass governance.
     * These cover metadata queries and dynamic validation patterns.
     */
    private static final List<Pattern> DEFAULT_PATTERN_BYPASSES = List.of(
        // Health check patterns with database-specific suffixes
        Pattern.compile("^SELECT\\s+1\\s*(?:FROM\\s+DUAL)?\\s*;?\\s*$", Pattern.CASE_INSENSITIVE),
        
        // Version and database info queries
        Pattern.compile("^SELECT\\s+(?:VERSION|@@VERSION|CURRENT_CATALOG).*$", Pattern.CASE_INSENSITIVE),
        
        // Database metadata queries (INFORMATION_SCHEMA)
        Pattern.compile("^SELECT\\s+.*\\s+FROM\\s+INFORMATION_SCHEMA\\b.*$", Pattern.CASE_INSENSITIVE),
        
        // System catalog queries (PostgreSQL)
        Pattern.compile("^SELECT\\s+.*\\s+FROM\\s+PG_(?:CATALOG|NAMESPACE|CLASS|TYPE|ATTRIBUTE)\\b.*$", Pattern.CASE_INSENSITIVE),
        
        // System views (SQL Server)
        Pattern.compile("^SELECT\\s+.*\\s+FROM\\s+SYS\\.(?:OBJECTS|TABLES|COLUMNS|DATABASES)\\b.*$", Pattern.CASE_INSENSITIVE),
        
        // MySQL SHOW commands
        Pattern.compile("^SHOW\\s+(?:DATABASES|TABLES|COLUMNS|VARIABLES|STATUS|PROCESSLIST)\\b.*$", Pattern.CASE_INSENSITIVE),
        
        // Oracle system tables
        Pattern.compile("^SELECT\\s+.*\\s+FROM\\s+(?:ALL_|USER_|DBA_)(?:TABLES|VIEWS|COLUMNS|OBJECTS)\\b.*$", Pattern.CASE_INSENSITIVE),
        
        // Connection testing with numeric results
        Pattern.compile("^SELECT\\s+\\d+\\s*(?:AS\\s+\\w+)?\\s*$", Pattern.CASE_INSENSITIVE)
    );
    
    private final boolean enabled;
    private final Set<String> exactBypasses;
    private final List<Pattern> patternBypasses;
    
    /**
     * Creates a FastPathBypass with default configuration.
     * Fast-path is enabled by default with standard health check patterns.
     */
    public FastPathBypass() {
        this(true, List.of());
    }
    
    /**
     * Creates a FastPathBypass with specified settings.
     * 
     * @param enabled Whether fast-path bypass is enabled
     * @param additionalPatterns Additional regex patterns to bypass (added to defaults)
     */
    public FastPathBypass(boolean enabled, List<String> additionalPatterns) {
        this.enabled = enabled;
        
        // Build exact bypasses (case-insensitive via uppercase normalization)
        this.exactBypasses = new HashSet<>(DEFAULT_EXACT_BYPASSES.size());
        for (String bypass : DEFAULT_EXACT_BYPASSES) {
            this.exactBypasses.add(normalizeForBypassCheck(bypass));
        }
        
        // Build pattern bypasses
        List<Pattern> patterns = new ArrayList<>(DEFAULT_PATTERN_BYPASSES);
        if (additionalPatterns != null) {
            for (String pattern : additionalPatterns) {
                try {
                    patterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
                } catch (Exception e) {
                    // Skip invalid patterns, log warning
                    System.err.println("FastPathBypass: Invalid bypass pattern ignored: " + pattern);
                }
            }
        }
        this.patternBypasses = List.copyOf(patterns); // Immutable copy
    }
    
    /**
     * Checks if a SQL query should bypass governance.
     * 
     * This method is optimized for performance:
     * 1. First checks for disabled state
     * 2. Then checks exact matches (O(1))
     * 3. Finally checks regex patterns (only if needed)
     * 
     * @param sql The SQL query to check
     * @return true if the query should bypass governance, false otherwise
     */
    public boolean shouldBypass(String sql) {
        if (!enabled || sql == null || sql.trim().isEmpty()) {
            return false;
        }
        
        String normalized = normalizeForBypassCheck(sql);
        
        // Fast path: exact match (O(1))
        if (exactBypasses.contains(normalized)) {
            return true;
        }
        
        // Slower path: regex patterns
        for (Pattern pattern : patternBypasses) {
            if (pattern.matcher(normalized).matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Normalizes SQL for bypass checking.
     * Trims whitespace, converts to uppercase, and removes trailing semicolons.
     */
    private String normalizeForBypassCheck(String sql) {
        return sql.trim().toUpperCase().replaceAll(";\\s*$", "");
    }
    
    /**
     * Returns whether fast-path bypass is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Returns the number of exact bypass patterns.
     */
    public int getExactBypassCount() {
        return exactBypasses.size();
    }
    
    /**
     * Returns the number of regex bypass patterns.
     */
    public int getPatternBypassCount() {
        return patternBypasses.size();
    }
}
