package com.enterprise.governance.core;

/**
 * SQL Parser for analyzing SQL queries.
 * Extracts structured information from raw SQL strings.
 */
public interface SQLParser {
    
    /**
     * Parses a SQL query and returns structured information.
     * 
     * @param sql Raw SQL query string
     * @return Parsed SQL information
     */
    ParsedSQL parse(String sql);
    
    /**
     * Value object containing parsed SQL information.
     */
    class ParsedSQL {
        private final String sql;
        private final SQLType type;
        private final String tableName;
        private final boolean hasWhereClause;
        private final boolean hasSelectAll;
        
        public ParsedSQL(String sql, SQLType type, String tableName, boolean hasWhereClause, boolean hasSelectAll) {
            this.sql = sql;
            this.type = type;
            this.tableName = tableName;
            this.hasWhereClause = hasWhereClause;
            this.hasSelectAll = hasSelectAll;
        }
        
        public String getSql() {
            return sql;
        }
        
        public SQLType getType() {
            return type;
        }
        
        public String getTableName() {
            return tableName;
        }
        
        public boolean hasWhereClause() {
            return hasWhereClause;
        }
        
        public boolean hasSelectAll() {
            return hasSelectAll;
        }
    }
    
    /**
     * Enum representing SQL statement types.
     */
    enum SQLType {
        SELECT,
        INSERT,
        UPDATE,
        DELETE,
        CREATE,
        DROP,
        ALTER,
        TRUNCATE,
        UNKNOWN
    }
}
