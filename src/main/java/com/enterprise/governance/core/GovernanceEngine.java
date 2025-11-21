package com.enterprise.governance.core;

import java.util.List;
import java.util.Map;

/**
 * CORE ENGINE: Central processing unit for Governance logic.
 * Input: Raw SQL + Params.
 * Output: Decision (Allow/Block).
 */
public interface GovernanceEngine {
    /**
     * Inspects SQL query and parameters against governance rules.
     *
     * @param sql SQL query to inspect
     * @param params Query parameters
     * @param context Additional context information (e.g., user, thread)
     * @return Decision whether to allow or block the query
     */
    GovernanceDecision inspect(String sql, List<Object> params, Map<String, String> context);
}
