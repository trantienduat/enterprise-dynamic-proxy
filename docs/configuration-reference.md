# Configuration Reference

Complete reference for all configuration options in the Enterprise Dynamic Proxy.

## Spring Boot Properties

All properties are prefixed with `enterprise.governance`:

### Core Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Master switch to enable/disable governance |
| `cache-size` | int | `1000` | Maximum number of cached governance decisions |
| `use-sql-parser` | boolean | `true` | Enable SQL parsing for rule evaluation |

### Rule Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `block-unsafe-deletes` | boolean | `true` | Block DELETE/UPDATE without WHERE clause |
| `block-schema-changes` | boolean | `true` | Block DDL operations (DROP, TRUNCATE, ALTER) |
| `warn-select-all` | boolean | `true` | Issue warnings for SELECT * queries |

### Performance Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enable-fast-path` | boolean | `true` | Enable fast-path bypass for health check queries |
| `bypass-patterns` | List&lt;String&gt; | `[]` | Additional regex patterns to bypass governance |

## Configuration Examples

### Development Environment

Allow more permissive queries in development:

```yaml
# application-dev.yml
enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: false      # Allow during development
    block-schema-changes: false      # Allow schema changes
    warn-select-all: true            # Keep warnings
    cache-size: 100                  # Smaller cache
```

### Production Environment

Strict governance in production:

```yaml
# application-prod.yml
enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: true       # Enforce WHERE clause
    block-schema-changes: true       # Block DDL
    warn-select-all: true            # Warn on SELECT *
    cache-size: 5000                 # Larger cache
```

### Testing Environment

Disable for unit tests:

```yaml
# application-test.yml
enterprise:
  governance:
    enabled: false                   # Disable completely
```

## Environment-Specific Configuration

### Profile-Based Settings

Adjust governance strictness per environment using Spring profiles:

```java
// Not needed - use YAML configuration instead!
// See examples below
```

### Development Profile

```yaml
# application-dev.yml
enterprise:
  governance:
    enabled: false                   # Disable entirely for local dev
```

### Staging Profile

```yaml
# application-staging.yml
enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: true       # Enforce WHERE clause
    block-schema-changes: false      # Allow schema changes
    warn-select-all: true            # Warn on SELECT *
```

### Production Profile

```yaml
# application-prod.yml
enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: true       # Enforce WHERE clause
    block-schema-changes: true       # Block all DDL
    warn-select-all: true            # Warn on SELECT *
    cache-size: 10000                # Larger cache
```

## Property Precedence

Configuration properties follow Spring Boot's standard precedence:

1. Command line arguments: `--enterprise.governance.enabled=false`
2. System properties: `-Denterprise.governance.enabled=false`
3. Environment variables: `ENTERPRISE_GOVERNANCE_ENABLED=false`
4. `application.yml` / `application.properties`
5. Default values

## Cache Configuration

### Cache Size Considerations

- **Small applications** (< 100 queries): `cache-size: 100-500`
- **Medium applications** (100-1000 queries): `cache-size: 1000-2000`
- **Large applications** (> 1000 queries): `cache-size: 5000-10000`

### Cache Behavior

- Cache uses LRU (Least Recently Used) eviction
- SQL is normalized (uppercase, trimmed) for cache keys
- Cache is thread-safe

## SQL Parser Configuration

### Enabling/Disabling Parser

```yaml
enterprise:
  governance:
    use-sql-parser: false  # Disable SQL parsing
```

When disabled:
- Table name extraction not available
- SQL type detection limited
- WHERE clause detection not available

### Custom SQL Parser

```java
@Bean
public SQLParser customSqlParser() {
    return new RegexSQLParser(); // Your custom implementation
}
```

## Logging Configuration

Control logging levels for governance:

```yaml
logging:
  level:
    com.enterprise.governance: DEBUG        # All governance logs
    com.enterprise.governance.core: INFO    # Core engine only
    com.enterprise.governance.jdbc: WARN    # JDBC adapter only
    com.enterprise.governance.r2dbc: ERROR  # R2DBC adapter only
```

## Performance Tuning

### High-Throughput Systems

```yaml
enterprise:
  governance:
    cache-size: 10000                # Large cache
    use-sql-parser: true             # Parser overhead is minimal
```

### Memory-Constrained Systems

```yaml
enterprise:
  governance:
    cache-size: 500                  # Smaller cache
    use-sql-parser: true             # Parser has low memory footprint
```

## Monitoring

### Metrics (Future Enhancement)

```yaml
management:
  metrics:
    enable:
      enterprise.governance: true
```

## Configuration Validation

The auto-configuration validates properties at startup:

- `cache-size` must be > 0
- Boolean properties accept: true/false, yes/no, on/off

Invalid configuration will cause startup failure:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Binding to target failed:
    Property: enterprise.governance.cache-size
    Value: -1
    Reason: must be greater than 0
```

## IDE Support

The library provides configuration metadata for IDE autocomplete:

- IntelliJ IDEA: Auto-completion in application.yml
- VS Code: Auto-completion with Spring Boot Extension
- Eclipse: Auto-completion with Spring Tools

## Next Steps

- Explore [advanced usage](advanced-usage.md) and custom rules
- Review [API reference](api-reference.md)
- Learn about [architecture](architecture.md)
