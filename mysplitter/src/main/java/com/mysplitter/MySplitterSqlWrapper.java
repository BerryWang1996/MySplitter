package com.mysplitter;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public class MySplitterSqlWrapper {

    public MySplitterSqlWrapper(String sql) {
        this.sql = sql;
    }

    private String sql;

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }
}
