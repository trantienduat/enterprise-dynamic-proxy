# Enterprise Dynamic Proxy Documentation

Welcome to the Enterprise Dynamic Proxy documentation. This database governance platform provides transparent SQL query interception for **all** Java persistence frameworks through lowest-level driver interception.

## Table of Contents

1. [Library Philosophy](philosophy.md) - **Start here!** Understand the opinionated approach
2. [Getting Started](getting-started.md) - Installation and basic usage
3. [Framework Integration](framework-integration.md) - **JPA, Hibernate, MyBatis, JdbcTemplate, JOOQ, etc.**
4. [Spring Boot Integration](spring-boot-integration.md) - Zero-configuration setup
5. [Configuration Reference](configuration-reference.md) - All configuration options
6. [Architecture](architecture.md) - Design patterns and architecture
7. [Advanced Usage](advanced-usage.md) - Distributed caching, monitoring, performance
8. [API Reference](api-reference.md) - Complete API documentation

## Quick Links

- **GitHub Repository**: [enterprise-dynamic-proxy](https://github.com/trantienduat/enterprise-dynamic-proxy)
- **Issues & Support**: [GitHub Issues](https://github.com/trantienduat/enterprise-dynamic-proxy/issues)

## Overview

The Enterprise Dynamic Proxy provides:

- **Framework-Agnostic Governance**: Works with JPA, Hibernate, MyBatis, JdbcTemplate, JOOQ, and any JDBC/R2DBC framework
- **Lowest-Level Interception**: Intercepts at DataSource/ConnectionFactory level - governs all operations
- **SQL Query Governance**: Block or warn on dangerous SQL operations
- **Transparent Integration**: Works with existing code - no changes required
- **Spring Boot Auto-Configuration**: Zero-configuration setup for Spring Boot applications
- **Performance Optimization**: Built-in caching for governance decisions
- **Opinionated by Design**: Enforces industry best practices via configuration only (no custom code required)

## Key Features

### Universal Framework Support

Whether you use:
- **JPA / Hibernate** - Entity operations, JPQL, native queries
- **MyBatis** - Mapper interfaces, XML queries
- **JdbcTemplate** - Spring JDBC operations
- **JOOQ** - Type-safe SQL queries
- **Spring Data** - JDBC or R2DBC repositories
- **Plain JDBC** - Direct Connection/Statement usage
- **Any other framework** - If it uses JDBC or R2DBC, it's governed!

See [Framework Integration Guide](framework-integration.md) for examples and details.

### Safety Rules

- Block DELETE/UPDATE without WHERE clause
- Block schema modifications (DROP, TRUNCATE, ALTER)
- Warn on SELECT * queries
- All rules configurable via YAML

### Technology Support

- **JDBC**: Synchronous database access using Dynamic Proxy pattern
- **R2DBC**: Reactive database access using Decorator pattern
- **Spring Boot**: Auto-configuration for seamless integration

### Architecture Patterns

- **Lowest-Level Interception**: Intercepts at DataSource/ConnectionFactory
- **Adapter Pattern**: Separate business logic from driver technologies
- **Onion Architecture**: Core logic independent of infrastructure
- **Decorator Pattern**: Non-invasive wrapper for R2DBC

## Next Steps

- [Get started](getting-started.md) with installation and basic usage
- Review [framework integration](framework-integration.md) for your framework (JPA, MyBatis, etc.)
- Learn about [Spring Boot integration](spring-boot-integration.md)
- Explore [advanced usage](advanced-usage.md) and custom rules
