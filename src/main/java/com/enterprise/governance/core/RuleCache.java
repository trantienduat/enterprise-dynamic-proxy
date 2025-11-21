package com.enterprise.governance.core;

import java.util.Optional;

/**
 * Cache interface for storing and retrieving governance decisions.
 * Helps improve performance by caching frequently used rule evaluations.
 */
public interface RuleCache {
    
    /**
     * Retrieves a cached decision for the given SQL query.
     * 
     * @param sql SQL query string (normalized)
     * @return Optional containing cached decision if exists
     */
    Optional<GovernanceDecision> get(String sql);
    
    /**
     * Stores a decision in the cache.
     * 
     * @param sql SQL query string (normalized)
     * @param decision The governance decision to cache
     */
    void put(String sql, GovernanceDecision decision);
    
    /**
     * Clears all cached entries.
     */
    void clear();
    
    /**
     * Gets the number of cached entries.
     * 
     * @return Number of entries in cache
     */
    int size();
}
