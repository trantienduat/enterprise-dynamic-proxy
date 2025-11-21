# Getting Started

This guide will help you get started with the Enterprise Dynamic Proxy for database governance.

## Requirements

- Java 21 or higher
- Gradle 8.5+ or Maven 3.6+
- (Optional) Spring Boot 3.2+ for auto-configuration

## Installation

### Gradle

Add the dependency to your `build.gradle`:

```gradle
dependencies {
    implementation 'com.enterprise:enterprise-dynamic-proxy:1.0.0-SNAPSHOT'
}
```

### Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.enterprise</groupId>
    <artifactId>enterprise-dynamic-proxy</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Basic Usage

### JDBC (Synchronous)

```java
import com.enterprise.governance.core.SimpleRuleEngine;
import com.enterprise.governance.jdbc.JdbcGovernance;
import javax.sql.DataSource;

// Create governance engine
GovernanceEngine engine = new SimpleRuleEngine();

// Wrap your existing DataSource
DataSource originalDataSource = // ... your DataSource
DataSource governedDataSource = JdbcGovernance.wrapDataSource(originalDataSource, engine);

// Use normally - governance is applied transparently
try (Connection conn = governedDataSource.getConnection();
     Statement stmt = conn.createStatement()) {
    
    // This will execute normally
    stmt.executeQuery("SELECT id, name FROM users WHERE id = 1");
    
    // This will throw SQLException with governance message
    stmt.execute("DELETE FROM users"); // Blocked: Missing WHERE clause
}
```

### R2DBC (Reactive)

```java
import com.enterprise.governance.core.SimpleRuleEngine;
import com.enterprise.governance.r2dbc.GovConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;

// Create governance engine
GovernanceEngine engine = new SimpleRuleEngine();

// Wrap your existing ConnectionFactory
ConnectionFactory originalFactory = // ... your ConnectionFactory
ConnectionFactory governedFactory = new GovConnectionFactory(originalFactory, engine);

// Use normally - governance is applied transparently
Mono.from(governedFactory.create())
    .flatMapMany(connection ->
        Flux.from(connection.createStatement("SELECT * FROM users WHERE id = $1")
                .bind(0, 1)
                .execute())
            .flatMap(result -> result.map((row, metadata) -> 
                row.get("name", String.class)))
            .doFinally(signal -> Mono.from(connection.close()).subscribe())
    )
    .subscribe();
```

## Default Governance Rules

The `SimpleRuleEngine` implements the following rules out of the box:

1. **Block DELETE/UPDATE without WHERE**: Prevents accidental mass deletion or updates
2. **Block schema modifications**: Prevents `DROP TABLE`, `TRUNCATE`, `ALTER TABLE`
3. **Warn on SELECT ***: Alerts on potentially inefficient queries

## Next Steps

- Learn about [Spring Boot integration](spring-boot-integration.md) for zero-configuration setup
- Explore [configuration options](configuration-reference.md) to customize rule behavior
- Review [advanced usage](advanced-usage.md) for monitoring and performance tuning
