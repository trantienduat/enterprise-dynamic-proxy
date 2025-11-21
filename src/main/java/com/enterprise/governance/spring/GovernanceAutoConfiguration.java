package com.enterprise.governance.spring;

import com.enterprise.governance.core.*;
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
    public GovernanceEngine governanceEngine(
            GovernanceProperties properties,
            SQLParser sqlParser,
            RuleCache ruleCache) {
        log.info("Creating SimpleRuleEngine with properties: blockUnsafeDeletes={}, blockSchemaChanges={}, warnSelectAll={}",
                properties.isBlockUnsafeDeletes(), properties.isBlockSchemaChanges(), properties.isWarnSelectAll());
        return new SimpleRuleEngine(sqlParser, ruleCache);
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
