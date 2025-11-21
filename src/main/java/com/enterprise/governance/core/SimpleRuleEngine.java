package com.enterprise.governance.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Simple implementation of GovernanceEngine with basic safety rules.
 * Uses SQLParser for query analysis and RuleCache for performance optimization.
 */
public class SimpleRuleEngine implements GovernanceEngine {
    
    private final SQLParser parser;
    private final RuleCache cache;
    
    /**
     * Creates a SimpleRuleEngine with default parser and cache.
     */
    public SimpleRuleEngine() {
        this(new BasicSQLParser(), new InMemoryRuleCache());
    }
    
    /**
     * Creates a SimpleRuleEngine with custom parser and cache.
     * 
     * @param parser SQL parser to use
     * @param cache Rule cache to use
     */
    public SimpleRuleEngine(SQLParser parser, RuleCache cache) {
        this.parser = parser;
        this.cache = cache;
    }
    
    @Override
    public GovernanceDecision inspect(String sql, List<Object> params, Map<String, String> context) {
        if (sql == null || sql.trim().isEmpty()) {
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
