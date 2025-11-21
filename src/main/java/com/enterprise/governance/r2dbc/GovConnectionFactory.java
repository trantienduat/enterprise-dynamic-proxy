package com.enterprise.governance.r2dbc;

import com.enterprise.governance.core.GovernanceEngine;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Wrapper for R2DBC ConnectionFactory (Entry Point).
 * This is the main integration point for R2DBC governance.
 */
public class GovConnectionFactory implements ConnectionFactory {
    private final ConnectionFactory delegate;
    private final GovernanceEngine engine;

    public GovConnectionFactory(ConnectionFactory delegate, GovernanceEngine engine) {
        this.delegate = delegate;
        this.engine = engine;
    }

    @Override
    public Publisher<? extends Connection> create() {
        return Mono.from(delegate.create())
                   .map(conn -> new GovConnection(conn, engine));
    }

    @Override
    public ConnectionFactoryMetadata getMetadata() { 
        return delegate.getMetadata(); 
    }
}
