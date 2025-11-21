# JPA/Hibernate Integration Example

This example demonstrates how the Enterprise Dynamic Proxy library automatically governs JPA/Hibernate operations through lowest-level DataSource interception.

## Project Setup

### build.gradle

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

dependencies {
    // Enterprise Dynamic Proxy - provides governance
    implementation 'com.enterprise:enterprise-dynamic-proxy:1.0.0-SNAPSHOT'
    
    // Spring Boot with JPA
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    
    // Database driver
    runtimeOnly 'org.postgresql:postgresql'
}
```

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    
  jpa:
    hibernate:
      ddl-auto: validate  # Don't let Hibernate modify schema - governance blocks it anyway
    show-sql: true
    properties:
      hibernate:
        format_sql: true

enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: true
    block-schema-changes: true
    warn-select-all: true
```

## Entity Definition

```java
package com.example.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String username;
    
    @Column(nullable = false, length = 255)
    private String email;
    
    @Column(nullable = false)
    private boolean active = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    // Constructors
    public User() {}
    
    public User(String username, String email) {
        this.username = username;
        this.email = email;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
}
```

## Repository

```java
package com.example.repository;

import com.example.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // ✅ SAFE: Query methods are always safe
    List<User> findByEmail(String email);
    List<User> findByActiveTrue();
    
    // ✅ SAFE: Has WHERE clause
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :loginTime WHERE u.id = :userId")
    int updateLastLogin(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime);
    
    // ✅ SAFE: Has WHERE clause
    @Modifying
    @Query("DELETE FROM User u WHERE u.active = false AND u.lastLogin < :cutoffDate")
    int deleteInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ❌ BLOCKED: No WHERE clause - will throw exception
    @Modifying
    @Query("DELETE FROM User u")
    int deleteAllUsers();
    
    // ❌ BLOCKED: No WHERE clause - will throw exception
    @Modifying
    @Query("UPDATE User u SET u.active = false")
    int deactivateAllUsers();
    
    // ⚠️ WARNED: SELECT * detected
    @Query(value = "SELECT * FROM users WHERE active = true", nativeQuery = true)
    List<User> findAllActiveNative();
}
```

## Service Layer

```java
package com.example.service;

import com.example.domain.User;
import com.example.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // ✅ SAFE: Standard repository operations
    public User createUser(String username, String email) {
        User user = new User(username, email);
        return userRepository.save(user);
    }
    
    // ✅ SAFE: Query with WHERE clause
    public List<User> findActiveUsers() {
        return userRepository.findByActiveTrue();
    }
    
    // ✅ SAFE: Update with WHERE clause
    @Transactional
    public void recordLogin(Long userId) {
        userRepository.updateLastLogin(userId, LocalDateTime.now());
    }
    
    // ✅ SAFE: Delete with WHERE clause
    @Transactional
    public int cleanupInactiveUsers(int daysInactive) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysInactive);
        return userRepository.deleteInactiveUsers(cutoff);
    }
    
    // ❌ BLOCKED: EntityManager with dangerous query
    @Transactional
    public void dangerousOperation() {
        // This will throw exception - blocked by governance
        try {
            entityManager.createQuery("DELETE FROM User").executeUpdate();
        } catch (Exception e) {
            System.err.println("Governance blocked: " + e.getMessage());
            throw e;
        }
    }
    
    // ❌ BLOCKED: Repository method without WHERE
    @Transactional
    public void extremelyDangerousOperation() {
        // This will throw exception - blocked by governance
        try {
            userRepository.deleteAllUsers();
        } catch (Exception e) {
            System.err.println("Governance blocked: " + e.getMessage());
            throw e;
        }
    }
    
    // ✅ SAFE: Native query with WHERE clause
    @Transactional
    public void deactivateUserById(Long userId) {
        entityManager.createNativeQuery(
            "UPDATE users SET active = false WHERE id = ?")
            .setParameter(1, userId)
            .executeUpdate();
    }
}
```

## Testing

```java
package com.example;

import com.example.domain.User;
import com.example.repository.UserRepository;
import com.example.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "enterprise.governance.enabled=true",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserServiceGovernanceTest {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testSafeOperations() {
        // Safe operations should work normally
        User user = userService.createUser("testuser", "test@example.com");
        assertNotNull(user.getId());
        
        userService.recordLogin(user.getId());
        
        List<User> activeUsers = userService.findActiveUsers();
        assertTrue(activeUsers.size() >= 1);
    }
    
    @Test
    void testGovernanceBlocksDangerousEntityManagerQuery() {
        // Dangerous EntityManager query should be blocked
        assertThrows(Exception.class, () -> {
            userService.dangerousOperation();
        });
    }
    
    @Test
    void testGovernanceBlocksDangerousRepositoryMethod() {
        // Dangerous repository method should be blocked
        assertThrows(Exception.class, () -> {
            userService.extremelyDangerousOperation();
        });
    }
    
    @Test
    void testGovernanceAllowsSafeNativeQuery() {
        // Safe native query with WHERE clause should work
        User user = userService.createUser("testuser2", "test2@example.com");
        assertDoesNotThrow(() -> {
            userService.deactivateUserById(user.getId());
        });
    }
}
```

## What Gets Governed

The governance intercepts **all** SQL at the DataSource level, which means:

### ✅ Governed Operations

1. **JPQL Queries**
   ```java
   entityManager.createQuery("DELETE FROM User WHERE id = ?")
   ```

2. **Native SQL Queries**
   ```java
   entityManager.createNativeQuery("DELETE FROM users WHERE id = ?")
   ```

3. **Repository Methods with @Query**
   ```java
   @Query("DELETE FROM User u WHERE u.active = false")
   ```

4. **Hibernate Criteria API**
   ```java
   session.createQuery("DELETE FROM User WHERE active = false")
   ```

5. **Spring Data JPA**
   - All generated queries (findBy*, deleteBy*, etc.)
   - Custom @Query annotations
   - @Modifying operations

### ❌ NOT Governed

1. **In-Memory Operations**
   ```java
   List<User> users = userRepository.findAll();
   users.removeIf(u -> !u.isActive());  // Java operation, not SQL
   ```

2. **Entity Operations (before SQL)**
   ```java
   User user = userRepository.findById(1L);
   user.setActive(false);  // Just modifying object
   userRepository.save(user);  // This generates SQL - governed!
   ```

## Best Practices

### 1. Always Use WHERE Clauses

```java
// ❌ BAD - Will be blocked
@Query("DELETE FROM User u")
int deleteAll();

// ✅ GOOD - Will work
@Query("DELETE FROM User u WHERE u.active = false")
int deleteInactive();
```

### 2. Use Specific Columns Instead of SELECT *

```java
// ⚠️ WARNED - Governance warns about SELECT *
@Query("SELECT u FROM User u")
List<User> findAll();

// ✅ BETTER - Specify columns
@Query("SELECT new User(u.id, u.username, u.email) FROM User u")
List<User> findAllOptimized();
```

### 3. Don't Rely on Hibernate DDL Auto

```yaml
# ❌ BAD - DDL operations are blocked anyway
spring.jpa.hibernate.ddl-auto: update

# ✅ GOOD - Use migration tools
spring.jpa.hibernate.ddl-auto: validate
```

Use Flyway or Liquibase for schema migrations instead.

### 4. Test with Governance Enabled

```java
@SpringBootTest
@TestPropertySource(properties = "enterprise.governance.enabled=true")
class MyTest {
    // Tests will catch governance violations
}
```

## Troubleshooting

### Issue: JPA queries not being governed

**Symptom:** Dangerous queries execute without being blocked.

**Cause:** Governance not enabled or DataSource not wrapped.

**Solution:** 
1. Verify `enterprise.governance.enabled=true` in application.yml
2. Check logs for "Wrapping DataSource with JDBC governance proxy"
3. Ensure Spring Boot auto-configuration is active

### Issue: Tests fail with governance

**Symptom:** Tests that worked before now fail.

**Cause:** Tests contain unsafe SQL that governance blocks.

**Solutions:**
1. Fix the tests to use safe SQL (add WHERE clauses)
2. Disable governance in tests:
   ```java
   @TestPropertySource(properties = "enterprise.governance.enabled=false")
   ```

### Issue: Hibernate DDL auto doesn't work

**Symptom:** Schema creation fails with governance errors.

**Cause:** Governance blocks DDL operations (DROP, CREATE, ALTER).

**Solution:** Use `ddl-auto: none` or `validate` and use migration tools (Flyway/Liquibase) for schema management.

## Summary

- ✅ Add dependency + configure YAML = All JPA operations governed
- ✅ Works with all JPA providers (Hibernate, EclipseLink, etc.)
- ✅ Intercepts JPQL, native SQL, Criteria API
- ✅ Blocks dangerous operations automatically
- ✅ No code changes required in entities/repositories
- ✅ Transparent and framework-agnostic

The governance happens at the **DataSource level**, making it impossible to bypass regardless of which JPA features you use!
