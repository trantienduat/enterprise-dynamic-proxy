# MyBatis Integration Example

This example demonstrates how the Enterprise Dynamic Proxy library automatically governs MyBatis operations through lowest-level DataSource interception.

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
    
    // Spring Boot with MyBatis
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3'
    
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

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.model
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl

enterprise:
  governance:
    enabled: true
    block-unsafe-deletes: true
    block-schema-changes: true
    warn-select-all: true
```

## Model

```java
package com.example.model;

import java.time.LocalDateTime;

public class Order {
    private Long id;
    private Long customerId;
    private String status;
    private Double totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors
    public Order() {}
    
    public Order(Long customerId, Double totalAmount) {
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

## Mapper Interface

```java
package com.example.mapper;

import com.example.model.Order;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    
    // ✅ SAFE: SELECT with WHERE clause
    @Select("SELECT * FROM orders WHERE customer_id = #{customerId}")
    List<Order> findByCustomerId(@Param("customerId") Long customerId);
    
    // ⚠️ WARNED: SELECT * detected
    @Select("SELECT * FROM orders WHERE status = #{status}")
    List<Order> findByStatus(@Param("status") String status);
    
    // ✅ SAFE: INSERT is always safe
    @Insert("INSERT INTO orders (customer_id, status, total_amount, created_at) " +
            "VALUES (#{customerId}, #{status}, #{totalAmount}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);
    
    // ✅ SAFE: UPDATE with WHERE clause
    @Update("UPDATE orders SET status = #{status}, updated_at = #{updatedAt} " +
            "WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, 
                     @Param("status") String status, 
                     @Param("updatedAt") LocalDateTime updatedAt);
    
    // ✅ SAFE: UPDATE with WHERE clause
    @Update("UPDATE orders SET status = 'CANCELLED', updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int cancelOrder(@Param("id") Long id);
    
    // ✅ SAFE: DELETE with WHERE clause
    @Delete("DELETE FROM orders WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
    
    // ✅ SAFE: DELETE with WHERE clause
    @Delete("DELETE FROM orders WHERE status = 'CANCELLED' AND created_at < #{cutoffDate}")
    int deleteCancelledOrders(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ❌ BLOCKED: DELETE without WHERE clause
    @Delete("DELETE FROM orders")
    int deleteAll();
    
    // ❌ BLOCKED: UPDATE without WHERE clause
    @Update("UPDATE orders SET status = 'ARCHIVED'")
    int archiveAll();
    
    // XML-mapped methods (defined in OrderMapper.xml)
    List<Order> findPendingOrders();
    int bulkUpdateStatus(@Param("oldStatus") String oldStatus, 
                         @Param("newStatus") String newStatus);
    int dangerousBulkDelete();
}
```

## XML Mapper

**src/main/resources/mapper/OrderMapper.xml:**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.mapper.OrderMapper">
    
    <!-- ✅ SAFE: SELECT with WHERE clause -->
    <select id="findPendingOrders" resultType="Order">
        SELECT id, customer_id, status, total_amount, created_at, updated_at
        FROM orders
        WHERE status = 'PENDING'
        ORDER BY created_at DESC
    </select>
    
    <!-- ✅ SAFE: UPDATE with WHERE clause -->
    <update id="bulkUpdateStatus">
        UPDATE orders
        SET status = #{newStatus},
            updated_at = NOW()
        WHERE status = #{oldStatus}
    </update>
    
    <!-- ❌ BLOCKED: DELETE without WHERE clause -->
    <delete id="dangerousBulkDelete">
        DELETE FROM orders
    </delete>
    
    <!-- ✅ SAFE: Dynamic WHERE clause (as long as it's not empty) -->
    <select id="searchOrders" resultType="Order" parameterType="map">
        SELECT * FROM orders
        <where>
            <if test="customerId != null">
                AND customer_id = #{customerId}
            </if>
            <if test="status != null">
                AND status = #{status}
            </if>
            <if test="minAmount != null">
                AND total_amount >= #{minAmount}
            </if>
        </where>
        ORDER BY created_at DESC
    </select>
    
    <!-- ⚠️ DANGEROUS: Could execute without WHERE if no params provided -->
    <!-- This pattern should be avoided - governance may not catch it -->
    <delete id="conditionalDelete" parameterType="map">
        DELETE FROM orders
        <where>
            <if test="orderId != null">
                AND id = #{orderId}
            </if>
        </where>
    </delete>
    
    <!-- ✅ BETTER: Always require WHERE clause -->
    <delete id="safeConditionalDelete">
        DELETE FROM orders
        WHERE id = #{orderId}
    </delete>
</mapper>
```

## Service Layer

```java
package com.example.service;

import com.example.mapper.OrderMapper;
import com.example.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {
    
    @Autowired
    private OrderMapper orderMapper;
    
    // ✅ SAFE: All operations have WHERE clauses
    public List<Order> getCustomerOrders(Long customerId) {
        return orderMapper.findByCustomerId(customerId);
    }
    
    // ✅ SAFE: SELECT with WHERE
    public List<Order> getPendingOrders() {
        return orderMapper.findPendingOrders();
    }
    
    // ✅ SAFE: INSERT is always safe
    @Transactional
    public Order createOrder(Long customerId, Double amount) {
        Order order = new Order(customerId, amount);
        orderMapper.insert(order);
        return order;
    }
    
    // ✅ SAFE: UPDATE with WHERE clause
    @Transactional
    public boolean processOrder(Long orderId) {
        int updated = orderMapper.updateStatus(
            orderId, 
            "PROCESSING", 
            LocalDateTime.now()
        );
        return updated > 0;
    }
    
    // ✅ SAFE: UPDATE with WHERE clause
    @Transactional
    public boolean cancelOrder(Long orderId) {
        int updated = orderMapper.cancelOrder(orderId);
        return updated > 0;
    }
    
    // ✅ SAFE: DELETE with WHERE clause
    @Transactional
    public int cleanupCancelledOrders(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        return orderMapper.deleteCancelledOrders(cutoff);
    }
    
    // ✅ SAFE: Bulk update with WHERE clause
    @Transactional
    public int archivePendingOrders() {
        return orderMapper.bulkUpdateStatus("PENDING", "ARCHIVED");
    }
    
    // ❌ BLOCKED: Dangerous delete without WHERE
    @Transactional
    public void dangerousDeleteAll() {
        try {
            orderMapper.deleteAll();
            // This line will never execute - exception thrown above
            System.out.println("All orders deleted");
        } catch (Exception e) {
            System.err.println("Governance blocked delete: " + e.getMessage());
            throw e;
        }
    }
    
    // ❌ BLOCKED: Dangerous update without WHERE
    @Transactional
    public void dangerousArchiveAll() {
        try {
            orderMapper.archiveAll();
            // This line will never execute - exception thrown above
            System.out.println("All orders archived");
        } catch (Exception e) {
            System.err.println("Governance blocked update: " + e.getMessage());
            throw e;
        }
    }
}
```

## Testing

```java
package com.example;

import com.example.mapper.OrderMapper;
import com.example.model.Order;
import com.example.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "enterprise.governance.enabled=true")
class OrderServiceGovernanceTest {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Test
    void testSafeOperations() {
        // Create order - safe
        Order order = orderService.createOrder(1L, 100.0);
        assertNotNull(order.getId());
        
        // Find by customer - safe (has WHERE)
        List<Order> orders = orderService.getCustomerOrders(1L);
        assertTrue(orders.size() >= 1);
        
        // Update with WHERE - safe
        boolean updated = orderService.processOrder(order.getId());
        assertTrue(updated);
    }
    
    @Test
    void testGovernanceBlocksDeleteAll() {
        // DELETE without WHERE should be blocked
        assertThrows(Exception.class, () -> {
            orderService.dangerousDeleteAll();
        });
    }
    
    @Test
    void testGovernanceBlocksUpdateAll() {
        // UPDATE without WHERE should be blocked
        assertThrows(Exception.class, () -> {
            orderService.dangerousArchiveAll();
        });
    }
    
    @Test
    void testGovernanceAllowsSafeDelete() {
        // DELETE with WHERE should work
        Order order = orderService.createOrder(2L, 50.0);
        orderMapper.updateStatus(order.getId(), "CANCELLED", LocalDateTime.now());
        
        assertDoesNotThrow(() -> {
            int deleted = orderService.cleanupCancelledOrders(0);
            assertTrue(deleted >= 1);
        });
    }
    
    @Test
    void testGovernanceAllowsBulkUpdateWithWhere() {
        // Bulk UPDATE with WHERE should work
        assertDoesNotThrow(() -> {
            int updated = orderService.archivePendingOrders();
            assertTrue(updated >= 0);
        });
    }
}
```

## What Gets Governed

### ✅ Governed Operations

1. **Annotation-based queries**
   ```java
   @Delete("DELETE FROM orders WHERE id = #{id}")
   ```

2. **XML-mapped queries**
   ```xml
   <delete id="deleteOld">
       DELETE FROM orders WHERE created_at < #{date}
   </delete>
   ```

3. **Dynamic SQL**
   ```xml
   <update id="updateOrders">
       UPDATE orders SET status = #{status}
       WHERE id IN
       <foreach item="id" collection="ids" open="(" separator="," close=")">
           #{id}
       </foreach>
   </update>
   ```

4. **SqlSession direct calls**
   ```java
   sqlSession.update("OrderMapper.updateStatus", params);
   ```

### Common Patterns

#### ✅ Safe Pattern: Always Include WHERE

```java
// Annotation
@Delete("DELETE FROM orders WHERE status = #{status}")
int deleteByStatus(@Param("status") String status);

// XML
<delete id="deleteByStatus">
    DELETE FROM orders WHERE status = #{status}
</delete>
```

#### ❌ Unsafe Pattern: No WHERE Clause

```java
// This will be BLOCKED
@Delete("DELETE FROM orders")
int deleteAll();

// This will be BLOCKED
<delete id="deleteAll">
    DELETE FROM orders
</delete>
```

#### ⚠️ Risky Pattern: Conditional WHERE

```xml
<!-- DANGEROUS: If no parameters provided, WHERE is empty -->
<delete id="conditionalDelete">
    DELETE FROM orders
    <where>
        <if test="id != null">AND id = #{id}</if>
    </where>
</delete>

<!-- BETTER: Always require at least one condition -->
<delete id="safeDelete">
    DELETE FROM orders
    WHERE id = #{id}
    <if test="status != null">AND status = #{status}</if>
</delete>
```

## Best Practices

### 1. Always Use WHERE Clauses

```xml
<!-- ❌ BAD -->
<delete id="deleteAll">
    DELETE FROM orders
</delete>

<!-- ✅ GOOD -->
<delete id="deleteOld">
    DELETE FROM orders WHERE created_at < #{cutoffDate}
</delete>
```

### 2. Avoid Empty Dynamic WHERE

```xml
<!-- ❌ BAD - Could execute without WHERE -->
<delete id="badDelete">
    DELETE FROM orders
    <where>
        <if test="status != null">status = #{status}</if>
    </where>
</delete>

<!-- ✅ GOOD - Always has WHERE condition -->
<delete id="goodDelete">
    DELETE FROM orders
    WHERE status = #{status}
</delete>
```

### 3. Use Specific Columns

```xml
<!-- ⚠️ WARNED - SELECT * -->
<select id="findAll" resultType="Order">
    SELECT * FROM orders
</select>

<!-- ✅ BETTER - Specific columns -->
<select id="findAll" resultType="Order">
    SELECT id, customer_id, status, total_amount
    FROM orders
</select>
```

### 4. Test with Governance Enabled

```java
@SpringBootTest
@TestPropertySource(properties = "enterprise.governance.enabled=true")
class MyTest {
    // Tests will catch governance violations
}
```

## Troubleshooting

### Issue: MyBatis queries not being governed

**Cause:** DataSource not wrapped or governance disabled.

**Solution:**
1. Verify `enterprise.governance.enabled=true`
2. Check logs for "Wrapping DataSource with JDBC governance proxy"
3. Ensure auto-configuration is active

### Issue: Dynamic SQL with empty WHERE passes governance

**Cause:** MyBatis generates "DELETE FROM table WHERE" (with empty condition), which may not be caught.

**Solution:** Avoid conditional WHERE clauses. Always require at least one condition:
```xml
<delete id="safeDelete">
    DELETE FROM orders WHERE id = #{id}
</delete>
```

### Issue: Batch operations are slow

**Cause:** Governance intercepts each statement, adding overhead.

**Solution:** 
1. Use batch mode with MyBatis (already optimized)
2. Ensure cache is properly sized:
   ```yaml
   enterprise.governance.cache-size: 5000
   ```

## Summary

- ✅ Add dependency + configure YAML = All MyBatis operations governed
- ✅ Works with annotation-based and XML-based mappers
- ✅ Intercepts dynamic SQL
- ✅ Blocks dangerous operations automatically
- ✅ No code changes required in mappers
- ✅ Transparent and framework-agnostic

The governance happens at the **DataSource level**, intercepting all SQL before it reaches the database - regardless of whether you use annotations, XML, or dynamic SQL!
