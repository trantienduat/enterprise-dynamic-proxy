# Spring Boot Integration

The Enterprise Dynamic Proxy provides seamless Spring Boot integration through auto-configuration.

## Setup

### 1. Add Dependency

Add the library to your Spring Boot project:

**Gradle:**
```gradle
dependencies {
    implementation 'com.enterprise:enterprise-dynamic-proxy:1.0.0-SNAPSHOT'
}
```

**Maven:**
```xml
<dependency>
    <groupId>com.enterprise</groupId>
    <artifactId>enterprise-dynamic-proxy</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Properties

Add configuration to your `application.yml`:

```yaml
enterprise:
  governance:
    enabled: true                    # Enable/disable governance (default: true)
    cache-size: 1000                 # Cache size for decisions (default: 1000)
    use-sql-parser: true             # Use SQL parser (default: true)
    block-unsafe-deletes: true       # Block DELETE/UPDATE without WHERE (default: true)
    block-schema-changes: true       # Block DDL operations (default: true)
    warn-select-all: true            # Warn on SELECT * (default: true)
```

Or in `application.properties`:

```properties
enterprise.governance.enabled=true
enterprise.governance.cache-size=1000
enterprise.governance.use-sql-parser=true
enterprise.governance.block-unsafe-deletes=true
enterprise.governance.block-schema-changes=true
enterprise.governance.warn-select-all=true
```

### 3. That's It!

The auto-configuration will automatically:
- Create `GovernanceEngine`, `SQLParser`, and `RuleCache` beans
- Wrap your `DataSource` (if JDBC is on classpath)
- Wrap your `ConnectionFactory` (if R2DBC is on classpath)

## JDBC Example

```java
@Service
public class UserService {
    
    private final JdbcTemplate jdbcTemplate;
    
    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;  // Already governed!
    }
    
    public List<User> findUsers() {
        // Governance is applied transparently
        return jdbcTemplate.query(
            "SELECT * FROM users WHERE active = true",
            new BeanPropertyRowMapper<>(User.class)
        );
    }
    
    public void dangerousOperation() {
        // This will throw DataAccessException due to governance blocking
        jdbcTemplate.update("DELETE FROM users"); // Blocked!
    }
}
```

## R2DBC Example

```java
@Service
public class ReactiveUserService {
    
    private final R2dbcEntityTemplate template;
    
    public ReactiveUserService(R2dbcEntityTemplate template) {
        this.template = template;  // Already governed!
    }
    
    public Flux<User> findActiveUsers() {
        // Governance is applied transparently
        return template.getDatabaseClient()
            .sql("SELECT * FROM users WHERE active = true")
            .map((row, metadata) -> /* map to User */)
            .all();
    }
}
```

## Custom Configuration

### Advanced Cache Configuration

For high-scale deployments, you can provide a distributed cache:

```java
@Configuration
public class DistributedCacheConfig {
    
    @Bean
    public RuleCache redisRuleCache(RedisTemplate<String, String> redisTemplate) {
        return new RedisRuleCache(redisTemplate);
    }
}
```

> **Note**: The governance engine rules are fixed and cannot be overridden. Use configuration properties to adjust rule behavior.

### Conditional Enabling

Disable governance for specific profiles:

```yaml
# application-dev.yml
enterprise:
  governance:
    enabled: false  # Disable in development

# application-prod.yml
enterprise:
  governance:
    enabled: true   # Enable in production
```

## Auto-Configuration Details

The auto-configuration:

1. **Checks if enabled**: Via `enterprise.governance.enabled` property
2. **Creates core beans**: `GovernanceEngine`, `SQLParser`, `RuleCache`
3. **Wraps DataSource**: If `javax.sql.DataSource` is on classpath
4. **Wraps ConnectionFactory**: If `io.r2dbc.spi.ConnectionFactory` is on classpath

### Bean Creation Order

```
GovernanceProperties
    ↓
SQLParser (BasicSQLParser)
    ↓
RuleCache (InMemoryRuleCache)
    ↓
GovernanceEngine (SimpleRuleEngine)
    ↓
DataSource/ConnectionFactory Wrappers
```

## Logging

The auto-configuration logs its activity at INFO level:

```
INFO  GovernanceAutoConfiguration - Creating BasicSQLParser for governance
INFO  GovernanceAutoConfiguration - Creating InMemoryRuleCache with size: 1000
INFO  GovernanceAutoConfiguration - Creating SimpleRuleEngine with properties: blockUnsafeDeletes=true, blockSchemaChanges=true, warnSelectAll=true
INFO  GovernanceAutoConfiguration - Wrapping DataSource with JDBC governance proxy
```

## Testing

### Disable in Tests

```java
@SpringBootTest(properties = "enterprise.governance.enabled=false")
class MyTest {
    // Governance disabled for this test
}
```

### Custom Test Configuration

```java
@TestConfiguration
public class TestGovernanceConfig {
    
    @Bean
    @Primary
    public GovernanceEngine testGovernanceEngine() {
        return new PermissiveRuleEngine(); // Allow everything in tests
    }
}
```

## Next Steps

- Review [configuration reference](configuration-reference.md) for all options
- Learn about [custom rules](advanced-usage.md#custom-rules)
- Explore [API reference](api-reference.md)
