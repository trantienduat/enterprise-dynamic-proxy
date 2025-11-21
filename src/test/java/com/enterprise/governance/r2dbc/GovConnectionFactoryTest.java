package com.enterprise.governance.r2dbc;

import com.enterprise.governance.core.GovernanceEngine;
import com.enterprise.governance.core.SimpleRuleEngine;
import io.r2dbc.h2.H2ConnectionConfiguration;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GovConnectionFactoryTest {

    private ConnectionFactory connectionFactory;
    private ConnectionFactory govConnectionFactory;
    private final GovernanceEngine engine = new SimpleRuleEngine();

    @BeforeEach
    void setUp() {
        // Create H2 in-memory connection factory
        connectionFactory = new H2ConnectionFactory(
                H2ConnectionConfiguration.builder()
                        .inMemory("testdb")
                        .build()
        );
        
        govConnectionFactory = new GovConnectionFactory(connectionFactory, engine);
    }

    @Test
    void testBlockDeleteWithoutWhere() {
        // This test checks that the governance engine blocks dangerous queries
        Mono<Connection> connectionMono = Mono.from(govConnectionFactory.create());
        
        StepVerifier.create(
                connectionMono.flatMapMany(connection ->
                        // First create table to avoid table-not-found errors masking governance errors
                        Flux.from(connection.createStatement("CREATE TABLE users (id INT, name VARCHAR(100))").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .thenMany(connection.createStatement("DELETE FROM users").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .doFinally(signal -> Mono.from(connection.close()).subscribe())
                )
        )
        .expectErrorMatches(throwable -> 
                throwable instanceof R2dbcDataIntegrityViolationException &&
                throwable.getMessage().contains("Governance Blocked") &&
                throwable.getMessage().contains("WHERE")
        )
        .verify();
    }

    @Test
    void testBlockUpdateWithoutWhere() {
        Mono<Connection> connectionMono = Mono.from(govConnectionFactory.create());
        
        StepVerifier.create(
                connectionMono.flatMapMany(connection ->
                        Flux.from(connection.createStatement("CREATE TABLE users (id INT, name VARCHAR(100))").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .thenMany(connection.createStatement("UPDATE users SET name = 'test'").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .doFinally(signal -> Mono.from(connection.close()).subscribe())
                )
        )
        .expectErrorMatches(throwable -> 
                throwable instanceof R2dbcDataIntegrityViolationException &&
                throwable.getMessage().contains("Governance Blocked") &&
                throwable.getMessage().contains("WHERE")
        )
        .verify();
    }

    @Test
    void testBlockDropTable() {
        Mono<Connection> connectionMono = Mono.from(govConnectionFactory.create());
        
        StepVerifier.create(
                connectionMono.flatMapMany(connection ->
                        Flux.from(connection.createStatement("CREATE TABLE users (id INT)").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .thenMany(connection.createStatement("DROP TABLE users").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .doFinally(signal -> Mono.from(connection.close()).subscribe())
                )
        )
        .expectErrorMatches(throwable -> 
                throwable instanceof R2dbcDataIntegrityViolationException &&
                throwable.getMessage().contains("Governance Blocked") &&
                throwable.getMessage().contains("modification")
        )
        .verify();
    }

    @Test
    void testAllowSafeQuery() {
        // Test that safe queries are allowed
        Mono<Connection> connectionMono = Mono.from(govConnectionFactory.create());
        
        StepVerifier.create(
                connectionMono.flatMapMany(connection ->
                        Flux.from(connection.createStatement("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100))").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .thenMany(connection.createStatement("INSERT INTO users VALUES (1, 'Alice')").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .thenMany(connection.createStatement("SELECT name FROM users WHERE id = 1").execute())
                                .flatMap(result -> Flux.from(result.map((row, metadata) -> 
                                        row.get("name", String.class))))
                                .doFinally(signal -> Mono.from(connection.close()).subscribe())
                )
        )
        .expectNext("Alice")
        .verifyComplete();
    }

    @Test
    void testAllowUpdateWithWhere() {
        Mono<Connection> connectionMono = Mono.from(govConnectionFactory.create());
        
        StepVerifier.create(
                connectionMono.flatMapMany(connection ->
                        Flux.from(connection.createStatement("CREATE TABLE users (id INT, active BOOLEAN)").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .thenMany(connection.createStatement("INSERT INTO users VALUES (1, true)").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .thenMany(connection.createStatement("UPDATE users SET active = false WHERE id = 1").execute())
                                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                                .doFinally(signal -> Mono.from(connection.close()).subscribe())
                )
        )
        .expectNext(1L)
        .verifyComplete();
    }
}

