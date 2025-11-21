package com.enterprise.governance.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleRuleEngineTest {

    private final GovernanceEngine engine = new SimpleRuleEngine();

    @Test
    void testAllowNormalSelect() {
        String sql = "SELECT id, name FROM users WHERE id = ?";
        List<Object> params = new ArrayList<>();
        params.add(1);
        Map<String, String> context = new HashMap<>();

        GovernanceDecision decision = engine.inspect(sql, params, context);

        assertEquals(GovernanceDecision.Action.ALLOW, decision.getAction());
        assertFalse(decision.isBlock());
    }

    @Test
    void testBlockDeleteWithoutWhere() {
        String sql = "DELETE FROM users";
        List<Object> params = new ArrayList<>();
        Map<String, String> context = new HashMap<>();

        GovernanceDecision decision = engine.inspect(sql, params, context);

        assertTrue(decision.isBlock());
        assertEquals(GovernanceDecision.Action.BLOCK, decision.getAction());
        assertTrue(decision.getReason().contains("WHERE"));
    }

    @Test
    void testBlockUpdateWithoutWhere() {
        String sql = "UPDATE users SET active = 1";
        List<Object> params = new ArrayList<>();
        Map<String, String> context = new HashMap<>();

        GovernanceDecision decision = engine.inspect(sql, params, context);

        assertTrue(decision.isBlock());
        assertEquals(GovernanceDecision.Action.BLOCK, decision.getAction());
        assertTrue(decision.getReason().contains("WHERE"));
    }

    @Test
    void testAllowUpdateWithWhere() {
        String sql = "UPDATE users SET active = 1 WHERE id = 5";
        List<Object> params = new ArrayList<>();
        Map<String, String> context = new HashMap<>();

        GovernanceDecision decision = engine.inspect(sql, params, context);

        assertFalse(decision.isBlock());
        assertEquals(GovernanceDecision.Action.ALLOW, decision.getAction());
    }

    @Test
    void testAllowDeleteWithWhere() {
        String sql = "DELETE FROM users WHERE id = 123";
        List<Object> params = new ArrayList<>();
        Map<String, String> context = new HashMap<>();

        GovernanceDecision decision = engine.inspect(sql, params, context);

        assertFalse(decision.isBlock());
        assertEquals(GovernanceDecision.Action.ALLOW, decision.getAction());
    }

    @Test
    void testBlockDropTable() {
        String sql = "DROP TABLE users";
        List<Object> params = new ArrayList<>();
        Map<String, String> context = new HashMap<>();

        GovernanceDecision decision = engine.inspect(sql, params, context);

        assertTrue(decision.isBlock());
        assertEquals(GovernanceDecision.Action.BLOCK, decision.getAction());
        assertTrue(decision.getReason().contains("modification"));
    }

    @Test
    void testBlockTruncate() {
        String sql = "TRUNCATE TABLE sessions";
        List<Object> params = new ArrayList<>();
        Map<String, String> context = new HashMap<>();

        GovernanceDecision decision = engine.inspect(sql, params, context);

        assertTrue(decision.isBlock());
        assertEquals(GovernanceDecision.Action.BLOCK, decision.getAction());
    }

    @Test
    void testBlockDropDatabase() {
        String sql = "DROP DATABASE mydb";
        List<Object> params = new ArrayList<>();
        Map<String, String> context = new HashMap<>();

        GovernanceDecision decision = engine.inspect(sql, params, context);

        assertTrue(decision.isBlock());
        assertEquals(GovernanceDecision.Action.BLOCK, decision.getAction());
        assertTrue(decision.getReason().contains("dropping"));
    }

    @Test
    void testWarnSelectStar() {
        String sql = "SELECT * FROM users WHERE id = 1";
        List<Object> params = new ArrayList<>();
        Map<String, String> context = new HashMap<>();

        GovernanceDecision decision = engine.inspect(sql, params, context);

        assertFalse(decision.isBlock());
        assertEquals(GovernanceDecision.Action.WARN, decision.getAction());
        assertTrue(decision.getReason().contains("SELECT *"));
    }

    @Test
    void testAllowNullOrEmptySQL() {
        GovernanceDecision decision1 = engine.inspect(null, new ArrayList<>(), new HashMap<>());
        assertEquals(GovernanceDecision.Action.ALLOW, decision1.getAction());

        GovernanceDecision decision2 = engine.inspect("", new ArrayList<>(), new HashMap<>());
        assertEquals(GovernanceDecision.Action.ALLOW, decision2.getAction());

        GovernanceDecision decision3 = engine.inspect("   ", new ArrayList<>(), new HashMap<>());
        assertEquals(GovernanceDecision.Action.ALLOW, decision3.getAction());
    }

    @Test
    void testCaseInsensitiveSQL() {
        String sql1 = "delete from users";
        String sql2 = "DELETE FROM users";
        String sql3 = "DeLeTe FrOm users";
        Map<String, String> context = new HashMap<>();

        assertTrue(engine.inspect(sql1, new ArrayList<>(), context).isBlock());
        assertTrue(engine.inspect(sql2, new ArrayList<>(), context).isBlock());
        assertTrue(engine.inspect(sql3, new ArrayList<>(), context).isBlock());
    }
}
