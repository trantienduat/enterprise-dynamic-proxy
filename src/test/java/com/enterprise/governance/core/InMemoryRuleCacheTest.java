package com.enterprise.governance.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRuleCacheTest {

    private RuleCache cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryRuleCache(3); // Small size for testing LRU
    }

    @Test
    void testPutAndGet() {
        String sql = "SELECT * FROM users";
        GovernanceDecision decision = GovernanceDecision.allow();
        
        cache.put(sql, decision);
        
        Optional<GovernanceDecision> cached = cache.get(sql);
        assertTrue(cached.isPresent());
        assertEquals(decision, cached.get());
    }

    @Test
    void testGetNonExistent() {
        Optional<GovernanceDecision> cached = cache.get("SELECT * FROM nonexistent");
        
        assertFalse(cached.isPresent());
    }

    @Test
    void testCaseInsensitiveNormalization() {
        String sql1 = "SELECT * FROM users";
        String sql2 = "select * from users";
        GovernanceDecision decision = GovernanceDecision.allow();
        
        cache.put(sql1, decision);
        
        Optional<GovernanceDecision> cached = cache.get(sql2);
        assertTrue(cached.isPresent());
        assertEquals(decision, cached.get());
    }

    @Test
    void testWhitespaceNormalization() {
        String sql1 = "  SELECT * FROM users  ";
        String sql2 = "SELECT * FROM users";
        GovernanceDecision decision = GovernanceDecision.warn("Test warning");
        
        cache.put(sql1, decision);
        
        Optional<GovernanceDecision> cached = cache.get(sql2);
        assertTrue(cached.isPresent());
        assertEquals(decision.getReason(), cached.get().getReason());
    }

    @Test
    void testClear() {
        cache.put("SELECT * FROM users", GovernanceDecision.allow());
        cache.put("SELECT * FROM orders", GovernanceDecision.allow());
        
        assertEquals(2, cache.size());
        
        cache.clear();
        
        assertEquals(0, cache.size());
        assertFalse(cache.get("SELECT * FROM users").isPresent());
    }

    @Test
    void testLRUEviction() {
        // Cache size is 3
        cache.put("SQL1", GovernanceDecision.allow());
        cache.put("SQL2", GovernanceDecision.allow());
        cache.put("SQL3", GovernanceDecision.allow());
        
        assertEquals(3, cache.size());
        
        // Adding a 4th item should evict the oldest (SQL1)
        cache.put("SQL4", GovernanceDecision.allow());
        
        assertEquals(3, cache.size());
        assertFalse(cache.get("SQL1").isPresent()); // Evicted
        assertTrue(cache.get("SQL2").isPresent());
        assertTrue(cache.get("SQL3").isPresent());
        assertTrue(cache.get("SQL4").isPresent());
    }

    @Test
    void testLRUAccessOrder() {
        cache.put("SQL1", GovernanceDecision.allow());
        cache.put("SQL2", GovernanceDecision.allow());
        cache.put("SQL3", GovernanceDecision.allow());
        
        // Access SQL1 to make it most recently used
        cache.get("SQL1");
        
        // Add new item - should evict SQL2 (least recently used)
        cache.put("SQL4", GovernanceDecision.allow());
        
        assertTrue(cache.get("SQL1").isPresent()); // Still present (accessed)
        assertFalse(cache.get("SQL2").isPresent()); // Evicted
        assertTrue(cache.get("SQL3").isPresent());
        assertTrue(cache.get("SQL4").isPresent());
    }

    @Test
    void testSize() {
        assertEquals(0, cache.size());
        
        cache.put("SQL1", GovernanceDecision.allow());
        assertEquals(1, cache.size());
        
        cache.put("SQL2", GovernanceDecision.block("Test"));
        assertEquals(2, cache.size());
        
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void testDifferentDecisionTypes() {
        cache.put("SQL1", GovernanceDecision.allow());
        cache.put("SQL2", GovernanceDecision.block("Blocked"));
        cache.put("SQL3", GovernanceDecision.warn("Warning"));
        
        assertEquals(GovernanceDecision.Action.ALLOW, cache.get("SQL1").get().getAction());
        assertEquals(GovernanceDecision.Action.BLOCK, cache.get("SQL2").get().getAction());
        assertEquals(GovernanceDecision.Action.WARN, cache.get("SQL3").get().getAction());
        assertEquals("Blocked", cache.get("SQL2").get().getReason());
        assertEquals("Warning", cache.get("SQL3").get().getReason());
    }
}
