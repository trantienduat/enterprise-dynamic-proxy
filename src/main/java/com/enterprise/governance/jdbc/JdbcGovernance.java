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
import java.util.*;
import javax.sql.DataSource;

/**
 * JDBC Adapter using Dynamic Proxy pattern to intercept database operations.
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
     */
    static class JdbcInvocationHandler implements InvocationHandler {
        private final Object target;
        private final GovernanceEngine engine;
        private final Map<Integer, Object> capturedParams = new HashMap<>();
        private String capturedSql;

        public JdbcInvocationHandler(Object target, GovernanceEngine engine) {
            this.target = target;
            this.engine = engine;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

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
                    
                    // Call Core Engine
                    GovernanceDecision decision = engine.inspect(sql, new ArrayList<>(capturedParams.values()), context);

                    if (decision.isBlock()) {
                        throw new SQLException("JDBC Governance Blocked: " + decision.getReason());
                    }
                    
                    // Log warnings but allow execution
                    if (decision.getAction() == GovernanceDecision.Action.WARN) {
                        System.err.println("JDBC Governance Warning: " + decision.getReason());
                    }
                }
            }

            // Proceed with original method if allowed
            return method.invoke(target, args);
        }
    }
}
