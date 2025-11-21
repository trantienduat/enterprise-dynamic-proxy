package com.enterprise.governance.core;

import java.util.List;
import java.util.Map;

/**
 * Simple implementation of GovernanceEngine with basic safety rules.
 */
public class SimpleRuleEngine implements GovernanceEngine {
    
    @Override
    public GovernanceDecision inspect(String sql, List<Object> params, Map<String, String> context) {
        if (sql == null || sql.trim().isEmpty()) {
            return GovernanceDecision.allow();
        }
        
        String upperSql = sql.toUpperCase().trim();
        
        // Rule 1: Block DELETE/UPDATE without WHERE clause (Naive check)
        if ((upperSql.startsWith("DELETE") || upperSql.startsWith("UPDATE")) 
                && !upperSql.contains("WHERE")) {
            return GovernanceDecision.block("Dangerous Query: Missing WHERE clause.");
        }

        // Rule 2: Block Schema modifications (DDL)
        if (upperSql.contains("DROP TABLE") || upperSql.contains("TRUNCATE")) {
            return GovernanceDecision.block("Schema modification is not allowed.");
        }

        // Rule 3: Block DROP DATABASE
        if (upperSql.contains("DROP DATABASE") || upperSql.contains("DROP SCHEMA")) {
            return GovernanceDecision.block("Database/Schema dropping is not allowed.");
        }

        // Rule 4: Warn on SELECT *
        if (upperSql.contains("SELECT *")) {
            return GovernanceDecision.warn("SELECT * may have performance implications.");
        }

        return GovernanceDecision.allow();
    }
}
