# Enterprise Dynamic Proxy Documentation

Welcome to the Enterprise Dynamic Proxy documentation. This database governance and observability platform provides transparent SQL query interception and validation for both synchronous (JDBC) and reactive (R2DBC) database drivers.

## Table of Contents

1. [Library Philosophy](philosophy.md) - **Start here!** Understand the opinionated approach
2. [Getting Started](getting-started.md) - Installation and basic usage
3. [Spring Boot Integration](spring-boot-integration.md) - Zero-configuration setup
4. [Configuration Reference](configuration-reference.md) - All configuration options
5. [Architecture](architecture.md) - Design patterns and architecture
6. [Advanced Usage](advanced-usage.md) - Distributed caching, monitoring, performance
7. [API Reference](api-reference.md) - Complete API documentation
8. [Risk & Limitations Analysis](risk-and-limitations.md) - **Enterprise adoption considerations**

## Quick Links

- **GitHub Repository**: [enterprise-dynamic-proxy](https://github.com/trantienduat/enterprise-dynamic-proxy)
- **Issues & Support**: [GitHub Issues](https://github.com/trantienduat/enterprise-dynamic-proxy/issues)

## Overview

The Enterprise Dynamic Proxy provides:

- **SQL Query Governance**: Block or warn on dangerous SQL operations
- **Transparent Integration**: Works with existing JDBC and R2DBC code
- **Spring Boot Auto-Configuration**: Zero-configuration setup for Spring Boot applications
- **Performance Optimization**: Built-in caching for governance decisions
- **Opinionated by Design**: Enforces industry best practices via configuration only (no custom code required)

## Key Features

### Safety Rules

- Block DELETE/UPDATE without WHERE clause
- Block schema modifications (DROP, TRUNCATE, ALTER)
- Warn on SELECT * queries
- Custom rule implementation support

### Technology Support

- **JDBC**: Synchronous database access using Dynamic Proxy pattern
- **R2DBC**: Reactive database access using Decorator pattern
- **Spring Boot**: Auto-configuration for seamless integration

### Architecture Patterns

- **Adapter Pattern**: Separate business logic from driver technologies
- **Onion Architecture**: Core logic independent of infrastructure
- **Decorator Pattern**: Non-invasive wrapper for R2DBC

## Next Steps

- [Get started](getting-started.md) with installation and basic usage
- Learn about [Spring Boot integration](spring-boot-integration.md)
- Explore [advanced usage](advanced-usage.md) and custom rules
