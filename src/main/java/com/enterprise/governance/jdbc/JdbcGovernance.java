package com.enterprise.governance.jdbc;

import com.enterprise.governance.core.GovernanceEngine;
import com.enterprise.governance.core.GovernanceDecision;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

/**
 * JDBC Adapter using Dynamic Proxy pattern to intercept database operations.
 * 
 * <h2>Thread Safety</h2>
 * This implementation is thread-safe. Each Statement proxy maintains its own
 * thread-safe parameter map using ConcurrentHashMap, and the SQL capture
 * is immutable after proxy creation.
 * 
 * <h2>JDBC Wrapper Support</h2>
 * This proxy correctly implements the java.sql.Wrapper interface, allowing
 * applications to unwrap to access the underlying JDBC driver implementation
 * for vendor-specific features (e.g., Oracle ARRAY, PostgreSQL PGConnection).
 * 
 * <h2>Integration with Connection Pools</h2>
 * This proxy is designed to work correctly when stacked on top of connection
 * pool proxies (HikariCP, Tomcat JDBC, etc.). The unwrap() implementation
 * delegates to the underlying connection, maintaining compatibility with
 * Spring's transaction synchronization.
 */
public class JdbcGovernance {

    /**
     * Helper to create Proxy for JDBC interfaces.
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Object target, Class<T> interfaceType, GovernanceEngine engine) {
        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[]{interfaceType},
                new JdbcInvocationHandler(target, engine)
        );
    }

    /**
     * Wraps a DataSource with governance capabilities.
     */
    public static DataSource wrapDataSource(DataSource dataSource, GovernanceEngine engine) {
        return createProxy(dataSource, DataSource.class, engine);
    }

    /**
     * Main Invocation Handler to intercept methods.
     * 
     * <h2>Thread Safety</h2>
     * This implementation is thread-safe:
     * - The target object and engine references are immutable
     * - Parameter capturing uses ConcurrentHashMap for thread-safe access
     * - Captured SQL is effectively immutable (set once during PreparedStatement creation)
     * 
     * <h2>JDBC Wrapper Interface</h2>
     * Properly implements unwrap() and isWrapperFor() to allow access to the
     * underlying JDBC driver implementation. This is critical for:
     * - Vendor-specific features (Oracle ARRAY, PostgreSQL PGConnection)
     * - Connection pool compatibility (HikariCP, Tomcat)
     * - Spring transaction management
     */
    static class JdbcInvocationHandler implements InvocationHandler {
        private final Object target;
        private final GovernanceEngine engine;
        
        // Thread-safe parameter map for concurrent PreparedStatement usage
        private final Map<Integer, Object> capturedParams = new ConcurrentHashMap<>();
        
        // SQL is effectively immutable - set once during PreparedStatement creation
        // Using volatile for visibility across threads
        private volatile String capturedSql;

        public JdbcInvocationHandler(Object target, GovernanceEngine engine) {
            this.target = target;
            this.engine = engine;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            // Handle java.sql.Wrapper interface methods for proper unwrapping
            if (methodName.equals("unwrap") && args != null && args.length == 1) {
                return handleUnwrap((Class<?>) args[0]);
            }
            
            if (methodName.equals("isWrapperFor") && args != null && args.length == 1) {
                return handleIsWrapperFor((Class<?>) args[0]);
            }

            // 1. Checkpoint: Wrap Connection when retrieved from DataSource
            if (methodName.equals("getConnection") && target instanceof DataSource) {
                Connection conn = (Connection) method.invoke(target, args);
                return createProxy(conn, Connection.class, engine);
            }

            // 2. Checkpoint: Wrap Statement when created from Connection
            if ((methodName.startsWith("createStatement") || methodName.startsWith("prepare")) 
                    && target instanceof Connection) {
                Object stmt = method.invoke(target, args);
                
                // Capture SQL for PreparedStatement
                if (methodName.startsWith("prepare") && args != null && args.length > 0 && args[0] instanceof String) {
                    JdbcInvocationHandler handler = new JdbcInvocationHandler(stmt, engine);
                    handler.capturedSql = (String) args[0];
                    return Proxy.newProxyInstance(
                            stmt.getClass().getClassLoader(),
                            stmt.getClass().getInterfaces(),
                            handler
                    );
                }
                
                return Proxy.newProxyInstance(
                        stmt.getClass().getClassLoader(),
                        stmt.getClass().getInterfaces(),
                        new JdbcInvocationHandler(stmt, engine)
                );
            }
            
            // 3. Capture Parameters (e.g., stmt.setString(1, "abc"))
            // Thread-safe: ConcurrentHashMap handles concurrent access
            if (methodName.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer) {
                capturedParams.put((Integer) args[0], args[1]);
            }

            // 4. Checkpoint: Intercept Execution (EXECUTE)
            if (methodName.startsWith("execute")) {
                String sql = capturedSql; // From PreparedStatement
                
                if (sql == null && args != null && args.length > 0 && args[0] instanceof String) {
                    sql = (String) args[0]; // Standard Statement
                }
                
                if (sql != null) {
                    // Create context with thread information
                    Map<String, String> context = new HashMap<>();
                    context.put("thread", Thread.currentThread().getName());
                    
                    // Create snapshot of params for thread-safety
                    List<Object> paramSnapshot = new ArrayList<>(capturedParams.values());
                    
                    // Call Core Engine
                    GovernanceDecision decision = engine.inspect(sql, paramSnapshot, context);

                    if (decision.isBlock()) {
                        throw new SQLException("JDBC Governance Blocked: " + decision.getReason());
                    }
                    
                    // Log warnings but allow execution
                    // Note: Using System.err for simplicity. Use SLF4J in production.
                    if (decision.getAction() == GovernanceDecision.Action.WARN) {
                        System.err.println("JDBC Governance Warning: " + decision.getReason());
                    }
                }
            }

            // Proceed with original method if allowed
            return method.invoke(target, args);
        }
        
        /**
         * Handles unwrap() calls for java.sql.Wrapper interface.
         * 
         * This allows applications to access vendor-specific features of the
         * underlying JDBC driver. The proxy checks if either:
         * 1. The target itself is an instance of the requested interface
         * 2. The target can be unwrapped to the requested interface (if it's a Wrapper)
         * 
         * @param iface The interface to unwrap to
         * @return The unwrapped object
         * @throws SQLException If the target cannot be unwrapped to the requested interface
         */
        @SuppressWarnings("unchecked")
        private <T> T handleUnwrap(Class<T> iface) throws SQLException {
            if (iface == null) {
                throw new SQLException("Interface argument must not be null");
            }
            
            // Check if target directly implements the interface
            if (iface.isInstance(target)) {
                return (T) target;
            }
            
            // If target is a Wrapper, delegate unwrap
            if (target instanceof Wrapper) {
                return ((Wrapper) target).unwrap(iface);
            }
            
            throw new SQLException("Cannot unwrap to " + iface.getName() + 
                    ". Target class: " + target.getClass().getName());
        }
        
        /**
         * Handles isWrapperFor() calls for java.sql.Wrapper interface.
         * 
         * @param iface The interface to check
         * @return true if the target can be unwrapped to the specified interface
         */
        private boolean handleIsWrapperFor(Class<?> iface) throws SQLException {
            if (iface == null) {
                throw new SQLException("Interface argument must not be null");
            }
            
            // Check if target directly implements the interface
            if (iface.isInstance(target)) {
                return true;
            }
            
            // If target is a Wrapper, delegate the check
            if (target instanceof Wrapper) {
                return ((Wrapper) target).isWrapperFor(iface);
            }
            
            return false;
        }
    }
}
