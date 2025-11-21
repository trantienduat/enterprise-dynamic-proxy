# Advanced Usage

This guide covers advanced topics including distributed caching, monitoring, performance tuning, and troubleshooting.

> **Important**: This library is **opinionated by design**. The governance rules are fixed and enforced through configuration only. You cannot implement custom `GovernanceEngine` implementations.

## Why Opinionated?

The library enforces industry best practices for database safety:
- ✅ **Consistency**: Same rules across all teams and services
- ✅ **Safety**: Prevents common SQL mistakes
- ✅ **Simplicity**: No code required, just configuration

If you need different behavior, use the configuration properties to enable/disable specific rules.

## Configuration-Based Customization

Instead of implementing custom engines, use configuration to control behavior:
enterprise:
  governance:
    # Disable specific rules you don't need
    block-unsafe-deletes: false      # Allow DELETE without WHERE
    block-schema-changes: true       # Still block DDL
    warn-select-all: false           # Don't warn on SELECT *
    
    # Or disable completely
    enabled: false
```

### Built-in Rules

The library enforces these rules (configurable via properties):

1. **Unsafe Deletes/Updates** (`block-unsafe-deletes`)
   - Blocks: `DELETE FROM users` (no WHERE)
   - Blocks: `UPDATE users SET active=false` (no WHERE)
   - Allows: `DELETE FROM users WHERE id = 1`

2. **Schema Changes** (`block-schema-changes`)
   - Blocks: `DROP TABLE users`
   - Blocks: `TRUNCATE TABLE users`
   - Blocks: `ALTER TABLE users ADD COLUMN email VARCHAR(255)`

3. **SELECT * Queries** (`warn-select-all`)
   - Warns: `SELECT * FROM users` (logs warning, still executes)
   - Allows: `SELECT id, name FROM users`

### Example: Permissive Development Environment

```yaml
# application-dev.yml
enterprise:
  governance:
    enabled: true                    # Keep governance active
    block-unsafe-deletes: false      # Allow for testing
    block-schema-changes: false      # Allow migrations
    warn-select-all: false           # Reduce noise
    cache-size: 100                  # Small cache
```

## Advanced SQL Parsing

### Using a Third-Party SQL Parser

For more sophisticated SQL parsing, integrate a library like JSqlParser:

```java
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;

public class JSqlParserAdapter implements SQLParser {
    
    @Override
    public Set<String> extractTableNames(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            List<String> tableList = tablesNamesFinder.getTableList(statement);
            return new HashSet<>(tableList);
        } catch (Exception e) {
            // Fallback to basic parsing
            return Set.of();
        }
    }
    
    @Override
    public String getSqlType(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            return statement.getClass().getSimpleName().replace("Statement", "");
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    @Override
    public boolean hasWhereClause(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            // Implementation depends on statement type
            return false; // Simplified
        } catch (Exception e) {
            return false;
        }
    }
}
```

Add dependency:

```gradle
dependencies {
    implementation 'com.github.jsqlparser:jsqlparser:4.6'
}
```

## Advanced Caching Strategies

### Distributed Cache with Redis

```java
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Duration;

public class RedisRuleCache implements RuleCache {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;
    
    public RedisRuleCache(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(1);
    }
    
    @Override
    public GovernanceDecision get(String sql) {
        String key = normalize(sql);
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? GovernanceDecision.valueOf(value) : null;
    }
    
    @Override
    public void put(String sql, GovernanceDecision decision) {
        String key = normalize(sql);
        redisTemplate.opsForValue().set(key, decision.name(), ttl);
    }
    
    @Override
    public void clear() {
        // Clear all governance cache keys
        // Implementation depends on key naming strategy
    }
    
    private String normalize(String sql) {
        return "governance::" + sql.toUpperCase().trim();
    }
}
```

Configuration:

```java
@Configuration
public class RedisCacheConfig {
    
    @Bean
    public RuleCache redisRuleCache(RedisTemplate<String, String> redisTemplate) {
        return new RedisRuleCache(redisTemplate);
    }
}
```

### Two-Level Cache (L1 + L2)

```java
public class TwoLevelRuleCache implements RuleCache {
    
    private final RuleCache l1Cache; // In-memory
    private final RuleCache l2Cache; // Redis
    
    public TwoLevelRuleCache(RuleCache l1Cache, RuleCache l2Cache) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
    }
    
    @Override
    public GovernanceDecision get(String sql) {
        // Check L1 first
        GovernanceDecision decision = l1Cache.get(sql);
        if (decision != null) {
            return decision;
        }
        
        // Check L2
        decision = l2Cache.get(sql);
        if (decision != null) {
            // Promote to L1
            l1Cache.put(sql, decision);
        }
        
        return decision;
    }
    
    @Override
    public void put(String sql, GovernanceDecision decision) {
        l1Cache.put(sql, decision);
        l2Cache.put(sql, decision);
    }
    
    @Override
    public void clear() {
        l1Cache.clear();
        l2Cache.clear();
    }
}
```

## Monitoring and Metrics

### Adding Micrometer Metrics

```java
import io.micrometer.core.instrument.*;

public class MetricsGovernanceEngine implements GovernanceEngine {
    
    private final GovernanceEngine delegate;
    private final MeterRegistry meterRegistry;
    
    public MetricsGovernanceEngine(
            GovernanceEngine delegate, 
            MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public GovernanceDecision inspect(String sql) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            GovernanceDecision decision = delegate.inspect(sql);
            
            // Record metrics
            sample.stop(Timer.builder("governance.inspect")
                .tag("decision", decision.name())
                .register(meterRegistry));
            
            meterRegistry.counter("governance.decisions", 
                "type", decision.name()).increment();
            
            return decision;
        } catch (Exception e) {
            meterRegistry.counter("governance.errors").increment();
            throw e;
        }
    }
}
```

### Logging Governance Decisions

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingGovernanceEngine implements GovernanceEngine {
    
    private static final Logger logger = 
        LoggerFactory.getLogger(LoggingGovernanceEngine.class);
    
    private final GovernanceEngine delegate;
    
    public LoggingGovernanceEngine(GovernanceEngine delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public GovernanceDecision inspect(String sql) {
        logger.debug("Inspecting SQL: {}", sql);
        
        GovernanceDecision decision = delegate.inspect(sql);
        
        switch (decision) {
            case BLOCK:
                logger.warn("BLOCKED SQL: {}", sql);
                break;
            case WARN:
                logger.info("WARNING for SQL: {}", sql);
                break;
            case ALLOW:
                logger.debug("ALLOWED SQL: {}", sql);
                break;
        }
        
        return decision;
    }
}
```

## Runtime Configuration Updates

### Using Spring Cloud Config

You can change governance settings at runtime using Spring Cloud Config:

```yaml
# config-server: application.yml
enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: ${BLOCK_UNSAFE_DELETES:true}
    block-schema-changes: ${BLOCK_SCHEMA_CHANGES:true}
```

Update environment variables without restarting:
```bash
# Enable stricter rules in production
export BLOCK_UNSAFE_DELETES=true
export BLOCK_SCHEMA_CHANGES=true

# Refresh configuration
curl -X POST http://localhost:8080/actuator/refresh
```

## Testing with Governance

### Disable Governance in Tests

Most tests should run without governance:

```java
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "enterprise.governance.enabled=false")
class UserServiceTest {
    
    @Autowired
    private UserService userService;
    
    @Test
    void testDeleteUser() {
        // Governance disabled - test can delete without WHERE
        userService.deleteAll();
    }
}
```

### Test Governance Rules

Create specific tests to verify governance behavior:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "enterprise.governance.enabled=true",
    "enterprise.governance.block-unsafe-deletes=true"
})
class GovernanceIntegrationTest {
    
    @Autowired
    private DataSource dataSource;
    
    @Test
    void shouldBlockUnsafeDelete() {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM users");
            }
        });
        
        assertTrue(exception.getMessage().contains("Governance"));
        assertTrue(exception.getMessage().contains("WHERE clause"));
    }
    
    @Test
    void shouldAllowSafeDelete() {
        assertDoesNotThrow(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM users WHERE id = 999");
            }
        });
    }
}
```

## Performance Tuning

### Benchmark Governance Overhead

```java
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class GovernanceBenchmark {
    
    private GovernanceEngine engine;
    
    @Setup
    public void setup() {
        SQLParser parser = new BasicSQLParser();
        RuleCache cache = new InMemoryRuleCache(1000);
        engine = new SimpleRuleEngine(parser, cache);
    }
    
    @Benchmark
    public GovernanceDecision benchmarkInspect() {
        return engine.inspect("SELECT * FROM users WHERE id = 1");
    }
}
```

## Troubleshooting

### Enable Debug Logging

```yaml
logging:
  level:
    com.enterprise.governance: DEBUG
```

### Common Issues

**Issue**: Governance not applied
- Check `enterprise.governance.enabled=true`
- Verify auto-configuration is active
- Check for custom GovernanceEngine bean

**Issue**: Poor performance
- Increase cache size
- Use two-level cache
- Consider distributed cache

**Issue**: False positives
- Customize GovernanceEngine
- Adjust rule sensitivity
- Use dynamic rule loading

## Next Steps

- Review [API reference](api-reference.md)
- Explore [architecture](architecture.md) details
- Check [configuration reference](configuration-reference.md)
