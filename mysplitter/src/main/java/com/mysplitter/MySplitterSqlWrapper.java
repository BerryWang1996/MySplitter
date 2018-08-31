package com.mysplitter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public class MySplitterSqlWrapper {

    private AtomicBoolean isRewrite = new AtomicBoolean(false);

    MySplitterSqlWrapper(String sql) {
        this.sql = sql;
        this.originalSql = sql;
    }

    private String originalSql;

    private String sql;

    public String getSql() {
        return sql;
    }

    String getOriginalSql() {
        return originalSql;
    }

    void rewrite(String sql) {
        if (isRewrite.compareAndSet(false, true)) {
            this.sql = sql;
        }
    }

}
