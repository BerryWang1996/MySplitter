package com.mysplitter.demo.datasource;

import com.mysplitter.advise.DatabasesRoutingHandlerAdvise;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public class DatabaseRouter implements DatabasesRoutingHandlerAdvise {

    @Override
    public String routerHandler(String sql) {
        if (sql.startsWith("[database-a]")) {
            return "database-a";
        } else {
            return "database-b";
        }
    }

    @Override
    public String rewriteSql(String sql) {
        if (sql.startsWith("[database-a]")) {
            return sql.substring("[database-a]".length());
        } else {
            return sql.substring("[database-b]".length());
        }
    }

}
