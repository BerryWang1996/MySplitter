package com.mysplitter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public class MySplitterSqlWrapper {

    private AtomicBoolean isRewrite = new AtomicBoolean(false);

    public MySplitterSqlWrapper(String sql) {
        this.sql = sql;
    }

    private String sql;

    public String getSql() {
        return sql;
    }

    public void rewrite(String sql) {
        if (isRewrite.compareAndSet(false, true)) {
            this.sql = sql;
        }
    }
}
