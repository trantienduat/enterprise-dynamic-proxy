package com.enterprise.governance.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for database governance.
 * Prefix: enterprise.governance
 */
@ConfigurationProperties(prefix = "enterprise.governance")
public class GovernanceProperties {
    
    /**
     * Enable or disable database governance
     */
    private boolean enabled = true;
    
    /**
     * Cache size for governance decisions
     */
    private int cacheSize = 1000;
    
    /**
     * Enable SQL parsing for better rule evaluation
     */
    private boolean useSqlParser = true;
    
    /**
     * Block DELETE/UPDATE without WHERE clause
     */
    private boolean blockUnsafeDeletes = true;
    
    /**
     * Block schema modifications (DROP, TRUNCATE, ALTER)
     */
    private boolean blockSchemaChanges = true;
    
    /**
     * Warn on SELECT * queries
     */
    private boolean warnSelectAll = true;

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public boolean isUseSqlParser() {
        return useSqlParser;
    }

    public void setUseSqlParser(boolean useSqlParser) {
        this.useSqlParser = useSqlParser;
    }

    public boolean isBlockUnsafeDeletes() {
        return blockUnsafeDeletes;
    }

    public void setBlockUnsafeDeletes(boolean blockUnsafeDeletes) {
        this.blockUnsafeDeletes = blockUnsafeDeletes;
    }

    public boolean isBlockSchemaChanges() {
        return blockSchemaChanges;
    }

    public void setBlockSchemaChanges(boolean blockSchemaChanges) {
        this.blockSchemaChanges = blockSchemaChanges;
    }

    public boolean isWarnSelectAll() {
        return warnSelectAll;
    }

    public void setWarnSelectAll(boolean warnSelectAll) {
        this.warnSelectAll = warnSelectAll;
    }
}
