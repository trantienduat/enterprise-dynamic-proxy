package com.enterprise.governance.r2dbc;

import com.enterprise.governance.core.GovernanceEngine;
import com.enterprise.governance.core.GovernanceDecision;
import io.r2dbc.spi.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;

import java.util.*;

/**
 * Wrapper for R2DBC Statement with governance interception.
 * Implements Fluent API by returning 'this' instead of delegate.
 * Note: Uses Object as key type for params to support both int and String bindings.
 * Avoid mixing bind(int, value) and bind(String, value) to prevent key conflicts.
 */
class GovStatement implements Statement {
    private final Statement delegate;
    private final String sql;
    private final GovernanceEngine engine;
    private final Map<Object, Object> params = new HashMap<>();

    public GovStatement(Statement delegate, String sql, GovernanceEngine engine) {
        this.delegate = delegate;
        this.sql = sql;
        this.engine = engine;
    }

    // Fluent API: Must return 'this' (wrapper) instead of delegate
    @Override
    public Statement bind(int index, Object value) {
        this.params.put(index, value);
        this.delegate.bind(index, value);
        return this; 
    }

    @Override
    public Statement bind(String name, Object value) {
        this.params.put(name, value);
        this.delegate.bind(name, value);
        return this;
    }

    @Override
    public Statement add() {
        this.delegate.add();
        return this;
    }

    // Execution Logic
    @Override
    public Publisher<? extends Result> execute() {
        // Create context
        Map<String, String> contextMap = new HashMap<>();
        contextMap.put("subscriber", "reactive-stream");
        
        // Call Core Engine (Fast in-memory check)
        GovernanceDecision decision = engine.inspect(this.sql, new ArrayList<>(params.values()), contextMap);

        // Block or Allow
        if (decision.isBlock()) {
            return Mono.error(new R2dbcDataIntegrityViolationException("R2DBC Governance Blocked: " + decision.getReason()));
        }

        // Log warnings but allow execution
        // Note: Using System.err for simplicity. Use SLF4J in production.
        if (decision.getAction() == GovernanceDecision.Action.WARN) {
            System.err.println("R2DBC Governance Warning: " + decision.getReason());
        }

        return delegate.execute();
    }
    
    // Other delegate methods
    @Override 
    public Statement bindNull(int index, Class<?> type) { 
        delegate.bindNull(index, type); 
        return this; 
    }
    
    @Override 
    public Statement bindNull(String name, Class<?> type) { 
        delegate.bindNull(name, type); 
        return this; 
    }
    
    @Override 
    public Statement returnGeneratedValues(String... columns) { 
        delegate.returnGeneratedValues(columns); 
        return this; 
    }
    
    @Override 
    public Statement fetchSize(int size) { 
        delegate.fetchSize(size); 
        return this; 
    }
}
