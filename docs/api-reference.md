# API Reference

Complete API documentation for the Enterprise Dynamic Proxy library.

## Package Structure

```
com.enterprise.governance
├── core                 # Core interfaces and implementations
│   ├── GovernanceEngine
│   ├── GovernanceDecision
│   ├── SimpleRuleEngine
│   ├── SQLParser
│   ├── BasicSQLParser
│   ├── RuleCache
│   └── InMemoryRuleCache
├── jdbc                 # JDBC adapter
│   └── JdbcGovernance
├── r2dbc                # R2DBC adapter
│   ├── GovConnectionFactory
│   ├── GovConnection
│   └── GovStatement
└── spring              # Spring Boot auto-configuration
    ├── GovernanceProperties
    └── GovernanceAutoConfiguration
```

---

## Core API

### GovernanceEngine

**Package**: `com.enterprise.governance.core`

**Purpose**: Interface for implementing SQL governance logic

```java
public interface GovernanceEngine {
    /**
     * Inspect SQL and return governance decision
     * 
     * @param sql The SQL statement to inspect
     * @return GovernanceDecision (ALLOW, BLOCK, or WARN)
     */
    GovernanceDecision inspect(String sql);
}
```

**Implementations**:
- `SimpleRuleEngine`: Default implementation with industry-standard safety rules (internal use only)

> **Note**: This interface is for internal use. Consumers should not implement custom `GovernanceEngine` classes. Use configuration properties instead.

**Example** (internal use):
```java
// This is handled automatically by Spring Boot auto-configuration
GovernanceEngine engine = new SimpleRuleEngine();
GovernanceDecision decision = engine.inspect("DELETE FROM users");
// Returns: BLOCK (missing WHERE clause)
```

---

### GovernanceDecision

**Package**: `com.enterprise.governance.core`

**Purpose**: Enum representing governance decision outcome

```java
public enum GovernanceDecision {
    ALLOW,    // SQL is safe, execute normally
    BLOCK,    // SQL is dangerous, throw exception
    WARN      // SQL is suboptimal, log warning and execute
}
```

**Methods**:
```java
// Get decision name
String name();

// Get decision by name
GovernanceDecision valueOf(String name);

// Get all possible decisions
GovernanceDecision[] values();
```

---

### SimpleRuleEngine

**Package**: `com.enterprise.governance.core`

**Purpose**: Default GovernanceEngine implementation with safety rules

**Constructor**:
```java
public SimpleRuleEngine();
public SimpleRuleEngine(SQLParser parser, RuleCache cache);
```

**Rules**:
1. **Block DELETE/UPDATE without WHERE**: Prevents accidental mass modifications
2. **Block schema changes**: Prevents DROP, TRUNCATE, ALTER operations
3. **Warn on SELECT ***: Alerts on potentially inefficient queries

**Example**:
```java
SQLParser parser = new BasicSQLParser();
RuleCache cache = new InMemoryRuleCache(1000);
GovernanceEngine engine = new SimpleRuleEngine(parser, cache);

// Blocked
engine.inspect("DELETE FROM users");              // BLOCK
engine.inspect("DROP TABLE users");                // BLOCK

// Warned
engine.inspect("SELECT * FROM users");             // WARN

// Allowed
engine.inspect("DELETE FROM users WHERE id = 1");  // ALLOW
```

---

### SQLParser

**Package**: `com.enterprise.governance.core`

**Purpose**: Interface for SQL parsing and analysis

```java
public interface SQLParser {
    /**
     * Extract table names from SQL
     * 
     * @param sql The SQL statement
     * @return Set of table names (uppercase)
     */
    Set<String> extractTableNames(String sql);
    
    /**
     * Determine SQL statement type
     * 
     * @param sql The SQL statement
     * @return SQL type (SELECT, INSERT, UPDATE, DELETE, etc.)
     */
    String getSqlType(String sql);
    
    /**
     * Check if SQL has WHERE clause
     * 
     * @param sql The SQL statement
     * @return true if WHERE clause present
     */
    boolean hasWhereClause(String sql);
}
```

**Implementations**:
- `BasicSQLParser`: Regex-based parser for common SQL patterns

---

### BasicSQLParser

**Package**: `com.enterprise.governance.core`

**Purpose**: Default SQLParser implementation using regex

**Constructor**:
```java
public BasicSQLParser();
```

**Features**:
- Extracts table names from SELECT, INSERT, UPDATE, DELETE, DROP, TRUNCATE
- Detects SQL type (SELECT, INSERT, UPDATE, DELETE, DROP, TRUNCATE, ALTER, CREATE)
- Detects WHERE clause presence
- Thread-safe (stateless)

**Example**:
```java
SQLParser parser = new BasicSQLParser();

Set<String> tables = parser.extractTableNames("SELECT * FROM users u JOIN orders o");
// Returns: ["USERS", "ORDERS"]

String type = parser.getSqlType("UPDATE users SET name = 'John'");
// Returns: "UPDATE"

boolean hasWhere = parser.hasWhereClause("DELETE FROM users WHERE id = 1");
// Returns: true
```

**Limitations**:
- Regex-based, may not handle all complex SQL
- Does not handle subqueries in table names
- Limited CTE (WITH clause) support

---

### RuleCache

**Package**: `com.enterprise.governance.core`

**Purpose**: Interface for caching governance decisions

```java
public interface RuleCache {
    /**
     * Get cached decision for SQL
     * 
     * @param sql The SQL statement
     * @return GovernanceDecision or null if not cached
     */
    GovernanceDecision get(String sql);
    
    /**
     * Cache a governance decision
     * 
     * @param sql The SQL statement
     * @param decision The decision to cache
     */
    void put(String sql, GovernanceDecision decision);
    
    /**
     * Clear all cached decisions
     */
    void clear();
}
```

**Implementations**:
- `InMemoryRuleCache`: LRU cache with configurable size

---

### InMemoryRuleCache

**Package**: `com.enterprise.governance.core`

**Purpose**: Default RuleCache implementation with LRU eviction

**Constructor**:
```java
public InMemoryRuleCache();              // Default size: 1000
public InMemoryRuleCache(int maxSize);   // Custom size
```

**Features**:
- LRU (Least Recently Used) eviction policy
- Thread-safe (synchronized methods)
- SQL normalization (uppercase + trim)
- O(1) get/put operations

**Example**:
```java
RuleCache cache = new InMemoryRuleCache(500);

cache.put("SELECT * FROM users", GovernanceDecision.WARN);
GovernanceDecision decision = cache.get("SELECT * FROM users");
// Returns: WARN

cache.clear(); // Remove all entries
```

---

## JDBC Adapter API

### JdbcGovernance

**Package**: `com.enterprise.governance.jdbc`

**Purpose**: Wrap JDBC DataSource with governance using Dynamic Proxy

**Methods**:
```java
/**
 * Wrap a DataSource with governance
 * 
 * @param dataSource The original DataSource
 * @param engine The governance engine
 * @return Governed DataSource proxy
 */
public static DataSource wrapDataSource(
    DataSource dataSource, 
    GovernanceEngine engine);
```

**Example**:
```java
// Create original DataSource
DataSource originalDataSource = new HikariDataSource(config);

// Create governance engine
GovernanceEngine engine = new SimpleRuleEngine();

// Wrap with governance
DataSource governedDataSource = JdbcGovernance.wrapDataSource(
    originalDataSource, 
    engine
);

// Use normally - governance is transparent
try (Connection conn = governedDataSource.getConnection();
     Statement stmt = conn.createStatement()) {
    
    // This will be blocked
    stmt.execute("DELETE FROM users"); 
}
```

**Implementation Details**:
- Uses `java.lang.reflect.Proxy`
- Wraps DataSource → Connection → Statement hierarchy
- Intercepts `execute*` methods on Statement
- Throws `SQLException` on BLOCK decision
- Logs warning on WARN decision

---

## R2DBC Adapter API

### GovConnectionFactory

**Package**: `com.enterprise.governance.r2dbc`

**Purpose**: Wrap R2DBC ConnectionFactory with governance

**Constructor**:
```java
public GovConnectionFactory(
    ConnectionFactory delegate, 
    GovernanceEngine engine);
```

**Methods**:
```java
// From ConnectionFactory interface
Publisher<? extends Connection> create();
ConnectionFactoryMetadata getMetadata();
```

**Example**:
```java
// Create original ConnectionFactory
ConnectionFactory originalFactory = ConnectionFactories.get(
    "r2dbc:h2:mem:///testdb"
);

// Create governance engine
GovernanceEngine engine = new SimpleRuleEngine();

// Wrap with governance
ConnectionFactory governedFactory = new GovConnectionFactory(
    originalFactory, 
    engine
);

// Use normally - governance is transparent
Mono.from(governedFactory.create())
    .flatMapMany(connection ->
        Flux.from(connection.createStatement("DELETE FROM users")
                .execute())
            .doFinally(signal -> Mono.from(connection.close()).subscribe())
    )
    .subscribe(); // Will emit error due to BLOCK decision
```

---

### GovConnection

**Package**: `com.enterprise.governance.r2dbc`

**Purpose**: Wrapper for R2DBC Connection with governance

**Constructor**:
```java
public GovConnection(Connection delegate, GovernanceEngine engine);
```

**Methods**:
```java
// From Connection interface
Publisher<Void> beginTransaction();
Publisher<Void> close();
Publisher<Void> commitTransaction();
Statement createStatement(String sql);
boolean isAutoCommit();
ConnectionMetadata getMetadata();
IsolationLevel getTransactionIsolationLevel();
Publisher<Void> releaseSavepoint(String name);
Publisher<Void> rollbackTransaction();
Publisher<Void> rollbackTransactionToSavepoint(String name);
Publisher<Void> setAutoCommit(boolean autoCommit);
Publisher<Void> setTransactionIsolationLevel(IsolationLevel level);
Publisher<Void> createSavepoint(String name);
Publisher<Boolean> validate(ValidationDepth depth);
```

**Key Behavior**:
- `createStatement(sql)`: Returns `GovStatement` with governance

---

### GovStatement

**Package**: `com.enterprise.governance.r2dbc`

**Purpose**: Wrapper for R2DBC Statement with governance and fluent API

**Constructor**:
```java
public GovStatement(
    Statement delegate, 
    String sql, 
    GovernanceEngine engine);
```

**Methods**:
```java
// From Statement interface
Statement add();
Statement bind(int index, Object value);
Statement bind(String name, Object value);
Statement bindNull(int index, Class<?> type);
Statement bindNull(String name, Class<?> type);
Publisher<? extends Result> execute();
Statement fetchSize(int rows);
Statement returnGeneratedValues(String... columns);
```

**Example**:
```java
GovStatement statement = (GovStatement) connection.createStatement(
    "SELECT * FROM users WHERE id = $1"
);

Flux.from(statement
        .bind(0, 1)
        .execute())
    .flatMap(result -> result.map((row, metadata) -> 
        row.get("name", String.class)))
    .subscribe(System.out::println);
```

**Key Behavior**:
- `execute()`: Checks governance before executing
- Emits `R2dbcException` on BLOCK decision
- Logs warning on WARN decision

---

## Spring Boot API

### GovernanceProperties

**Package**: `com.enterprise.governance.spring`

**Purpose**: Configuration properties for Spring Boot

**Annotation**: `@ConfigurationProperties("enterprise.governance")`

**Properties**:
```java
private boolean enabled = true;
private int cacheSize = 1000;
private boolean useSqlParser = true;
private boolean blockUnsafeDeletes = true;
private boolean blockSchemaChanges = true;
private boolean warnSelectAll = true;
```

**Example**:
```yaml
enterprise:
  governance:
    enabled: true
    cache-size: 2000
    use-sql-parser: true
    block-unsafe-deletes: true
    block-schema-changes: true
    warn-select-all: true
```

---

### GovernanceAutoConfiguration

**Package**: `com.enterprise.governance.spring`

**Purpose**: Spring Boot auto-configuration class

**Annotation**: `@AutoConfiguration`

**Conditions**:
- Enabled when `enterprise.governance.enabled=true`
- Creates beans when not already defined

**Beans Created**:
1. `SQLParser` → `BasicSQLParser`
2. `RuleCache` → `InMemoryRuleCache`
3. `GovernanceEngine` → `SimpleRuleEngine`
4. `DataSource` → Wrapped with JDBC governance (if JDBC on classpath)
5. `ConnectionFactory` → Wrapped with R2DBC governance (if R2DBC on classpath)

**Example**:
```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
        // GovernanceAutoConfiguration automatically applied
    }
}
```

---

## Thread Safety

### Thread-Safe Components

- `SimpleRuleEngine`: Thread-safe (uses thread-safe dependencies)
- `BasicSQLParser`: Thread-safe (stateless)
- `InMemoryRuleCache`: Thread-safe (synchronized methods)
- `JdbcGovernance`: Thread-safe (proxies are stateless)
- `GovConnectionFactory`: Thread-safe (immutable)

### Non-Thread-Safe Components

- `GovConnection`: Not thread-safe (same as R2DBC Connection)
- `GovStatement`: Not thread-safe (same as R2DBC Statement)

**Best Practice**: Create new Connection/Statement per request/transaction

---

## Exception Handling

### JDBC

**Exception**: `java.sql.SQLException`

**Message Format**: 
- `"Governance: SQL statement blocked - [reason]"` (BLOCK)
- `"Governance: Warning - [reason]"` (WARN, logged only)

**Example**:
```java
try {
    stmt.execute("DELETE FROM users");
} catch (SQLException e) {
    // e.getMessage() = "Governance: SQL statement blocked - DELETE/UPDATE without WHERE clause"
}
```

### R2DBC

**Exception**: `io.r2dbc.spi.R2dbcException`

**Message Format**:
- `"Governance: SQL statement blocked - [reason]"` (BLOCK)

**Example**:
```java
Mono.from(statement.execute())
    .onErrorResume(R2dbcException.class, e -> {
        // e.getMessage() = "Governance: SQL statement blocked - DROP/TRUNCATE operations"
        return Mono.empty();
    })
    .subscribe();
```

---

## Versioning

**Current Version**: 1.0.0-SNAPSHOT

**Compatibility**:
- Java: 21+
- Spring Boot: 3.2+ (optional)
- JDBC: Any JDBC 4.2+ driver
- R2DBC: R2DBC SPI 1.0.0+

---

## Next Steps

- Review [architecture](architecture.md) for design patterns
- Explore [advanced usage](advanced-usage.md) for customization
- Check [getting started](getting-started.md) for quick setup
