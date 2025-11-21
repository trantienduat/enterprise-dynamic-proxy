package com.enterprise.governance.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Basic SQL parser implementation using regex patterns.
 * This is a simple implementation; for production use, consider using
 * a full SQL parser library like JSQLParser.
 */
public class BasicSQLParser implements SQLParser {
    
    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_ALL_PATTERN = Pattern.compile("SELECT\\s+\\*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(
        "(?:FROM|INTO|UPDATE)\\s+([\\w.]+)|(?:TRUNCATE\\s+TABLE|DROP\\s+TABLE)\\s+([\\w.]+)|(?:CREATE|ALTER)\\s+TABLE\\s+([\\w.]+)", 
        Pattern.CASE_INSENSITIVE
    );
    
    @Override
    public ParsedSQL parse(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return new ParsedSQL(sql, SQLType.UNKNOWN, null, false, false);
        }
        
        String upperSql = sql.toUpperCase().trim();
        SQLType type = detectSQLType(upperSql);
        String tableName = extractTableName(sql);
        boolean hasWhere = WHERE_PATTERN.matcher(sql).find();
        boolean hasSelectAll = SELECT_ALL_PATTERN.matcher(sql).find();
        
        return new ParsedSQL(sql, type, tableName, hasWhere, hasSelectAll);
    }
    
    private SQLType detectSQLType(String upperSql) {
        if (upperSql.startsWith("SELECT")) {
            return SQLType.SELECT;
        } else if (upperSql.startsWith("INSERT")) {
            return SQLType.INSERT;
        } else if (upperSql.startsWith("UPDATE")) {
            return SQLType.UPDATE;
        } else if (upperSql.startsWith("DELETE")) {
            return SQLType.DELETE;
        } else if (upperSql.startsWith("CREATE")) {
            return SQLType.CREATE;
        } else if (upperSql.startsWith("DROP")) {
            return SQLType.DROP;
        } else if (upperSql.startsWith("ALTER")) {
            return SQLType.ALTER;
        } else if (upperSql.startsWith("TRUNCATE")) {
            return SQLType.TRUNCATE;
        }
        return SQLType.UNKNOWN;
    }
    
    private String extractTableName(String sql) {
        Matcher matcher = TABLE_NAME_PATTERN.matcher(sql);
        if (matcher.find()) {
            // Check all capture groups (regex has multiple groups for different SQL patterns)
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null) {
                    return group;
                }
            }
        }
        return null;
    }
}
