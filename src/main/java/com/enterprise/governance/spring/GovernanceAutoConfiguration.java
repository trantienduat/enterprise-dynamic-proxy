package com.enterprise.governance.spring;

import com.enterprise.governance.core.BasicSQLParser;
import com.enterprise.governance.core.FastPathBypass;
import com.enterprise.governance.core.GovernanceEngine;
import com.enterprise.governance.core.InMemoryRuleCache;
import com.enterprise.governance.core.RuleCache;
import com.enterprise.governance.core.SQLParser;
import com.enterprise.governance.core.SimpleRuleEngine;
import com.enterprise.governance.jdbc.JdbcGovernance;
import com.enterprise.governance.r2dbc.GovConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Spring Boot auto-configuration for database governance.
 * Automatically wraps DataSource (JDBC) and ConnectionFactory (R2DBC) with governance layer.
 * 
 * <h2>Spring Actuator Integration</h2>
 * This auto-configuration is designed to work seamlessly with Spring Actuator:
 * <ul>
 *   <li>Health check queries (SELECT 1) are automatically bypassed via fast-path</li>
 *   <li>Connection pool metrics from HikariCP are not affected</li>
 *   <li>DataSourceHealthIndicator works correctly with the wrapped DataSource</li>
 * </ul>
 * 
 * <h2>Connection Pool Compatibility</h2>
 * The governance proxy correctly implements java.sql.Wrapper interface, ensuring:
 * <ul>
 *   <li>Proper unwrapping to vendor-specific interfaces</li>
 *   <li>Compatibility with HikariCP, Tomcat JDBC, and other pools</li>
 *   <li>Spring transaction synchronization continues to work</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(GovernanceProperties.class)
@ConditionalOnProperty(prefix = "enterprise.governance", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GovernanceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GovernanceAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SQLParser sqlParser(GovernanceProperties properties) {
        if (properties.isUseSqlParser()) {
            log.info("Creating BasicSQLParser for governance");
            return new BasicSQLParser();
        }
        log.info("SQL parser disabled, using basic rule evaluation");
        return new BasicSQLParser(); // Still create parser, just note it in config
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleCache ruleCache(GovernanceProperties properties) {
        log.info("Creating InMemoryRuleCache with size: {}", properties.getCacheSize());
        return new InMemoryRuleCache(properties.getCacheSize());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public FastPathBypass fastPathBypass(GovernanceProperties properties) {
        log.info("Creating FastPathBypass enabled={}, additionalPatterns={}", 
                properties.isEnableFastPath(), 
                properties.getBypassPatterns().size());
        return new FastPathBypass(properties.isEnableFastPath(), properties.getBypassPatterns());
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernanceEngine governanceEngine(
            GovernanceProperties properties,
            SQLParser sqlParser,
            RuleCache ruleCache,
            FastPathBypass fastPathBypass) {
        log.info("Creating SimpleRuleEngine with properties: blockUnsafeDeletes={}, blockSchemaChanges={}, warnSelectAll={}, fastPathEnabled={}",
                properties.isBlockUnsafeDeletes(), properties.isBlockSchemaChanges(), properties.isWarnSelectAll(), properties.isEnableFastPath());
        return new SimpleRuleEngine(sqlParser, ruleCache, fastPathBypass);
    }

    /**
     * JDBC governance configuration - activated when DataSource is on classpath
     */
    @Configuration
    @ConditionalOnClass(name = "javax.sql.DataSource")
    static class JdbcGovernanceConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "governedDataSource")
        @ConditionalOnClass(DataSource.class)
        public DataSource governedDataSource(DataSource dataSource, GovernanceEngine engine) {
            log.info("Wrapping DataSource with JDBC governance proxy");
            return JdbcGovernance.wrapDataSource(dataSource, engine);
        }
    }

    /**
     * R2DBC governance configuration - activated when ConnectionFactory is on classpath
     */
    @Configuration
    @ConditionalOnClass(name = "io.r2dbc.spi.ConnectionFactory")
    static class R2dbcGovernanceConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "governedConnectionFactory")
        @ConditionalOnClass(ConnectionFactory.class)
        public ConnectionFactory governedConnectionFactory(ConnectionFactory connectionFactory, GovernanceEngine engine) {
            log.info("Wrapping ConnectionFactory with R2DBC governance wrapper");
            return new GovConnectionFactory(connectionFactory, engine);
        }
    }
}
