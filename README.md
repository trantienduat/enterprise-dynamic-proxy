# Enterprise Dynamic Proxy

A Database Governance & Observability Platform that provides transparent SQL query interception and validation for both synchronous (JDBC) and reactive (R2DBC) database drivers.

## Overview

This project implements a governance layer that intercepts database operations and applies safety rules before executing queries. It uses:

- **Adapter Pattern** for JDBC (using Dynamic Proxy)
- **Decorator Pattern** for R2DBC (using Wrapper classes)
- **Onion Architecture** to separate business logic from driver technologies

## Architecture

The system consists of three main layers:

### 1. Core Layer (Pure Logic)
- **GovernanceEngine**: Interface for implementing governance rules
- **SimpleRuleEngine**: Default implementation with basic safety rules
- **GovernanceDecision**: Value object representing allow/block/warn decisions

### 2. JDBC Adapter (Synchronous)
- Uses Java's Dynamic Proxy (`java.lang.reflect.Proxy`)
- Automatically intercepts JDBC method calls
- Transparent integration with existing JDBC code

### 3. R2DBC Adapter (Reactive)
- Uses Decorator/Wrapper pattern
- Maintains Fluent API contract
- Fully non-blocking and reactive

## Features

The default `SimpleRuleEngine` implements the following safety rules:

1. **Block DELETE/UPDATE without WHERE clause**: Prevents accidental mass deletion or updates
2. **Block schema modifications**: Prevents `DROP TABLE`, `TRUNCATE`, `DROP DATABASE`
3. **Warn on SELECT ***: Alerts on potentially inefficient queries

## Usage

### JDBC Example

```java
import com.enterprise.governance.core.SimpleRuleEngine;
import com.enterprise.governance.jdbc.JdbcGovernance;
import javax.sql.DataSource;

// Wrap your existing DataSource
GovernanceEngine engine = new SimpleRuleEngine();
DataSource originalDataSource = // ... your DataSource
DataSource governedDataSource = JdbcGovernance.wrapDataSource(originalDataSource, engine);

// Use the governed DataSource normally
try (Connection conn = governedDataSource.getConnection();
     Statement stmt = conn.createStatement()) {
    
    // This will execute normally
    stmt.executeQuery("SELECT id, name FROM users WHERE id = 1");
    
    // This will throw SQLException with governance message
    stmt.execute("DELETE FROM users"); // Blocked: Missing WHERE clause
}
```

### R2DBC Example

```java
import com.enterprise.governance.core.SimpleRuleEngine;
import com.enterprise.governance.r2dbc.GovConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;

// Wrap your existing ConnectionFactory
GovernanceEngine engine = new SimpleRuleEngine();
ConnectionFactory originalFactory = // ... your ConnectionFactory
ConnectionFactory governedFactory = new GovConnectionFactory(originalFactory, engine);

// Use the governed ConnectionFactory normally
Mono.from(governedFactory.create())
    .flatMapMany(connection ->
        Flux.from(connection.createStatement("SELECT * FROM users WHERE id = $1")
                .bind(0, 1)
                .execute())
            .flatMap(result -> /* process result */)
            .doFinally(signal -> Mono.from(connection.close()).subscribe())
    )
    .subscribe();
```

## Custom Governance Rules

You can implement your own governance rules by implementing the `GovernanceEngine` interface:

```java
public class CustomRuleEngine implements GovernanceEngine {
    @Override
    public GovernanceDecision inspect(String sql, List<Object> params, Map<String, String> context) {
        // Your custom logic here
        if (sql.contains("sensitive_table")) {
            return GovernanceDecision.block("Access to sensitive data denied");
        }
        return GovernanceDecision.allow();
    }
}
```

## Building

```bash
mvn clean package
```

## Testing

```bash
mvn test
```

Test coverage includes:
- Core governance engine rules
- JDBC proxy interception
- R2DBC wrapper interception
- Both allow and block scenarios

## Dependencies

- Java 11 or higher
- R2DBC SPI 1.0.0.RELEASE
- Reactor Core 3.5.0
- JUnit 5 for testing

## License

This project is provided as-is for educational and demonstration purposes.

## Design Principles

1. **Separation of Concerns**: Business logic is completely independent of driver technologies
2. **Non-Invasive**: Works transparently with existing code
3. **Type-Safe**: Leverages compile-time checking
4. **Reactive-Ready**: Full support for reactive programming with R2DBC
5. **Extensible**: Easy to add custom rules and logic

## Future Enhancements

Potential areas for extension:
- SQL parsing for more sophisticated rule checking
- Metrics and observability integration
- Rule caching and optimization
- Support for prepared statement analysis
- Integration with external policy engines
- Query performance monitoring
- Audit logging capabilities
