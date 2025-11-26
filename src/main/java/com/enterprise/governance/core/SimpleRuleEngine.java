package com.enterprise.governance.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Simple implementation of GovernanceEngine with basic safety rules.
 * Uses SQLParser for query analysis and RuleCache for performance optimization.
 * 
 * <h2>Fast-Path Optimization</h2>
 * This engine supports a fast-path bypass mechanism for health check and validation
 * queries. When enabled, queries like "SELECT 1" skip the parsing and rule evaluation
 * entirely, reducing overhead for connection pool validation and Spring Actuator health checks.
 * 
 * <h2>Thread Safety</h2>
 * This class is thread-safe. The cache implementation handles synchronization,
 * and the fast-path bypass is immutable after construction.
 */
public class SimpleRuleEngine implements GovernanceEngine {
    
    private final SQLParser parser;
    private final RuleCache cache;
    private final FastPathBypass fastPath;
    
    /**
     * Creates a SimpleRuleEngine with default parser, cache, and fast-path enabled.
     */
    public SimpleRuleEngine() {
        this(new BasicSQLParser(), new InMemoryRuleCache(), new FastPathBypass());
    }
    
    /**
     * Creates a SimpleRuleEngine with custom parser and cache, fast-path enabled by default.
     * 
     * @param parser SQL parser to use
     * @param cache Rule cache to use
     */
    public SimpleRuleEngine(SQLParser parser, RuleCache cache) {
        this(parser, cache, new FastPathBypass());
    }
    
    /**
     * Creates a SimpleRuleEngine with custom parser, cache, and fast-path configuration.
     * 
     * @param parser SQL parser to use
     * @param cache Rule cache to use
     * @param fastPath Fast-path bypass configuration
     */
    public SimpleRuleEngine(SQLParser parser, RuleCache cache, FastPathBypass fastPath) {
        this.parser = parser;
        this.cache = cache;
        this.fastPath = fastPath != null ? fastPath : new FastPathBypass(false, List.of());
    }
    
    @Override
    public GovernanceDecision inspect(String sql, List<Object> params, Map<String, String> context) {
        if (sql == null || sql.trim().isEmpty()) {
            return GovernanceDecision.allow();
        }
        
        // Fast-path: bypass governance for trusted queries (health checks, metadata)
        // This avoids the parsing penalty for connection pool validation and actuator health checks
        if (fastPath.shouldBypass(sql)) {
            return GovernanceDecision.allow();
        }
        
        // Check cache first
        Optional<GovernanceDecision> cachedDecision = cache.get(sql);
        if (cachedDecision.isPresent()) {
            return cachedDecision.get();
        }
        
        // Parse SQL using parser
        SQLParser.ParsedSQL parsed = parser.parse(sql);
        
        // Evaluate rules
        GovernanceDecision decision = evaluateRules(parsed);
        
        // Cache the decision
        cache.put(sql, decision);
        
        return decision;
    }
    
    private GovernanceDecision evaluateRules(SQLParser.ParsedSQL parsed) {
        // Rule 1: Block DELETE/UPDATE without WHERE clause
        if ((parsed.getType() == SQLParser.SQLType.DELETE || parsed.getType() == SQLParser.SQLType.UPDATE) 
                && !parsed.hasWhereClause()) {
            return GovernanceDecision.block("Dangerous Query: Missing WHERE clause.");
        }

        // Rule 2: Block Schema modifications (DDL)
        if (parsed.getType() == SQLParser.SQLType.DROP || parsed.getType() == SQLParser.SQLType.TRUNCATE) {
            return GovernanceDecision.block("Schema modification is not allowed.");
        }

        // Rule 3: Block ALTER statements
        if (parsed.getType() == SQLParser.SQLType.ALTER) {
            return GovernanceDecision.block("Schema alteration is not allowed.");
        }

        // Rule 4: Warn on SELECT *
        if (parsed.hasSelectAll()) {
            return GovernanceDecision.warn("SELECT * may have performance implications.");
        }

        return GovernanceDecision.allow();
    }
}
