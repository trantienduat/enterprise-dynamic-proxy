package com.enterprise.governance.r2dbc;

import com.enterprise.governance.core.GovernanceEngine;
import io.r2dbc.spi.*;
import org.reactivestreams.Publisher;

/**
 * Wrapper for R2DBC Connection with governance interception.
 */
class GovConnection implements Connection {
    private final Connection delegate;
    private final GovernanceEngine engine;

    public GovConnection(Connection delegate, GovernanceEngine engine) {
        this.delegate = delegate;
        this.engine = engine;
    }

    @Override
    public Statement createStatement(String sql) {
        return new GovStatement(delegate.createStatement(sql), sql, engine);
    }

    // Batch operations
    @Override
    public Batch createBatch() {
        return delegate.createBatch();
    }

    // Transaction methods
    @Override 
    public Publisher<Void> close() { 
        return delegate.close(); 
    }
    
    @Override 
    public Publisher<Void> beginTransaction() { 
        return delegate.beginTransaction(); 
    }
    
    @Override 
    public Publisher<Void> commitTransaction() { 
        return delegate.commitTransaction(); 
    }
    
    @Override 
    public Publisher<Void> rollbackTransaction() { 
        return delegate.rollbackTransaction(); 
    }
    
    @Override 
    public ConnectionMetadata getMetadata() { 
        return delegate.getMetadata(); 
    }
    
    @Override 
    public Publisher<Void> setTransactionIsolationLevel(IsolationLevel isolationLevel) { 
        return delegate.setTransactionIsolationLevel(isolationLevel); 
    }
    
    @Override 
    public Publisher<Void> setAutoCommit(boolean autoCommit) { 
        return delegate.setAutoCommit(autoCommit); 
    }
    
    @Override 
    public Publisher<Boolean> validate(ValidationDepth depth) { 
        return delegate.validate(depth); 
    }
    
    @Override 
    public Publisher<Void> createSavepoint(String name) { 
        return delegate.createSavepoint(name); 
    }
    
    @Override 
    public Publisher<Void> releaseSavepoint(String name) { 
        return delegate.releaseSavepoint(name); 
    }
    
    @Override 
    public Publisher<Void> rollbackTransactionToSavepoint(String name) { 
        return delegate.rollbackTransactionToSavepoint(name); 
    }
    
    @Override 
    public boolean isAutoCommit() { 
        return delegate.isAutoCommit(); 
    }

    @Override
    public IsolationLevel getTransactionIsolationLevel() {
        return delegate.getTransactionIsolationLevel();
    }

    @Override
    public Publisher<Void> beginTransaction(TransactionDefinition definition) {
        return delegate.beginTransaction(definition);
    }

    @Override
    public Publisher<Void> setLockWaitTimeout(java.time.Duration timeout) {
        return delegate.setLockWaitTimeout(timeout);
    }

    @Override
    public Publisher<Void> setStatementTimeout(java.time.Duration timeout) {
        return delegate.setStatementTimeout(timeout);
    }
}
