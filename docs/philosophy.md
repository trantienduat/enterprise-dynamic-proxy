# Library Philosophy

## Opinionated by Design

This library is **intentionally opinionated** to enforce database safety across your organization with zero resistance.

### Why Opinionated?

#### 1. **Consistency Across Teams**
- All services follow the same governance rules
- No "creative interpretations" of best practices
- Standardized behavior organization-wide

#### 2. **Prevent Common Mistakes**
- DELETE without WHERE clause → Production incident
- SELECT * → Performance degradation
- DROP TABLE → Data loss

These are **objectively bad patterns**, not subjective preferences.

#### 3. **Zero Configuration Burden**
- Add dependency → Rules automatically applied
- No code to write
- No decisions to make (initially)

#### 4. **Safety by Default**
- Strictest settings are the defaults
- Developers must explicitly relax rules (with awareness)
- Prevents "I didn't know" incidents

## Design Principles

### Principle 1: Configuration Over Code

**Bad** (requires code):
```java
@Bean
public GovernanceEngine customEngine() {
    return new MyCustomRules(); // Every team different!
}
```

**Good** (configuration only):
```yaml
enterprise:
  governance:
    block-unsafe-deletes: true  # Consistent across org
```

### Principle 2: Safe Defaults

All rules are enabled by default:
- `block-unsafe-deletes: true`
- `block-schema-changes: true`
- `warn-select-all: true`

To relax rules, you must **explicitly configure**:
```yaml
enterprise:
  governance:
    block-unsafe-deletes: false  # Consciously accepting risk
```

This creates awareness and documentation of risk acceptance.

### Principle 3: Environment-Based Flexibility

Different environments have different needs:

```yaml
# application-dev.yml (permissive)
enterprise:
  governance:
    enabled: false

# application-prod.yml (strict)
enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: true
    block-schema-changes: true
```

This allows:
- **Development**: Fast iteration without governance
- **Staging**: Test with production-like rules
- **Production**: Full enforcement

### Principle 4: No Escape Hatches

You **cannot**:
- Override `GovernanceEngine` with custom implementation
- Bypass governance programmatically
- Create "special case" rules per service

You **can**:
- Disable governance entirely (`enabled: false`)
- Disable specific rules (`block-unsafe-deletes: false`)
- Use different settings per environment

## Benefits

### For Platform Teams

✅ **Enforce standards** without relying on code review  
✅ **Reduce incidents** caused by dangerous SQL  
✅ **Audit compliance** via configuration  
✅ **Easy rollout** - just add to dependencies  

### For Development Teams

✅ **No learning curve** - works automatically  
✅ **No code to write** - just configuration  
✅ **Clear errors** when rules are violated  
✅ **Flexible** in dev, strict in prod  

### For Organizations

✅ **Consistent behavior** across all services  
✅ **Reduced risk** of data loss/corruption  
✅ **Lower operational burden** - fewer incidents  
✅ **Compliance** - centralized control  

## Comparison with Alternatives

### Alternative 1: Code Review
- **Problem**: Doesn't scale, easy to miss
- **Solution**: Automated enforcement at runtime

### Alternative 2: Database Permissions
- **Problem**: Too coarse-grained, affects all apps
- **Solution**: Application-level governance per service

### Alternative 3: Static Analysis
- **Problem**: Only catches what's in code, not dynamic SQL
- **Solution**: Runtime interception catches everything

### Alternative 4: Custom Per-Service Rules
- **Problem**: Inconsistent, maintenance burden, drift
- **Solution**: Centralized opinionated library

## What If I Need Custom Rules?

### Short Answer
**You probably don't.** The built-in rules cover 95% of common safety issues.

### If You Really Need Custom Rules

**Option 1**: Request a feature
- Open GitHub issue
- Explain use case
- We'll add it to the library (benefits everyone)

**Option 2**: Fork the library
- Create your own fork
- Add custom rules
- Maintain it yourself (not recommended)

**Option 3**: Use a different approach
- Database triggers
- External policy engine (OPA)
- Application-level checks before calling DB

**What We Won't Support**: Per-service custom `GovernanceEngine` implementations.
- Defeats the purpose (consistency)
- Creates maintenance burden
- Leads to drift and incidents

## Configuration as the Interface

The **public API** of this library is the configuration properties:

```yaml
enterprise.governance.enabled                 # Master switch
enterprise.governance.cache-size              # Performance tuning
enterprise.governance.block-unsafe-deletes    # Rule: DELETE/UPDATE WHERE
enterprise.governance.block-schema-changes    # Rule: DDL operations
enterprise.governance.warn-select-all         # Rule: SELECT *
```

These properties are:
- **Stable**: Won't break between versions
- **Well-documented**: Clear behavior
- **Auditable**: Stored in git with code
- **Environment-aware**: Different per profile

## Migration Path

### Phase 1: Observability (Recommended Start)
```yaml
enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: false    # Don't block yet
    block-schema-changes: false    # Don't block yet
    warn-select-all: true          # Just warn
```

**Goal**: See what would be blocked, fix issues

### Phase 2: Incremental Enforcement
```yaml
enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: true     # Start blocking
    block-schema-changes: false    # Still allowing
    warn-select-all: true          # Still warning
```

**Goal**: Enforce safest rule first

### Phase 3: Full Enforcement
```yaml
enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: true     # Blocking
    block-schema-changes: true     # Blocking
    warn-select-all: true          # Warning
```

**Goal**: Full governance active

## Summary

This library is **opinionated by design** because:

1. Database safety is not subjective
2. Consistency reduces operational burden
3. Configuration is better than code for governance
4. Safe defaults prevent incidents
5. Environment flexibility allows gradual adoption

**If you want custom rules per service, this is not the right library.**

**If you want organization-wide database safety with minimal effort, this is perfect.**
