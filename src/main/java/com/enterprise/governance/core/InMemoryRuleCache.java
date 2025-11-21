package com.enterprise.governance.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of RuleCache using LRU eviction strategy.
 * Thread-safe implementation for concurrent access.
 */
public class InMemoryRuleCache implements RuleCache {
    
    private final Map<String, GovernanceDecision> cache;
    private final int maxSize;
    
    /**
     * Creates a cache with default maximum size of 1000 entries.
     */
    public InMemoryRuleCache() {
        this(1000);
    }
    
    /**
     * Creates a cache with specified maximum size.
     * 
     * @param maxSize Maximum number of entries to cache
     */
    public InMemoryRuleCache(int maxSize) {
        this.maxSize = maxSize;
        // LRU cache using LinkedHashMap with access order
        this.cache = new LinkedHashMap<String, GovernanceDecision>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, GovernanceDecision> eldest) {
                return InMemoryRuleCache.this.size() > InMemoryRuleCache.this.maxSize;
            }
        };
    }
    
    @Override
    public synchronized Optional<GovernanceDecision> get(String sql) {
        String normalizedSql = normalizeSql(sql);
        return Optional.ofNullable(cache.get(normalizedSql));
    }
    
    @Override
    public synchronized void put(String sql, GovernanceDecision decision) {
        String normalizedSql = normalizeSql(sql);
        cache.put(normalizedSql, decision);
    }
    
    @Override
    public synchronized void clear() {
        cache.clear();
    }
    
    @Override
    public synchronized int size() {
        return cache.size();
    }
    
    /**
     * Normalizes SQL by trimming and converting to uppercase.
     * This helps with cache hit rate for similar queries.
     */
    private String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.trim().toUpperCase();
    }
}
