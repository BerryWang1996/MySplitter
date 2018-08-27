package com.mysplitter.demo.datasource;

import com.mysplitter.advise.DatabasesRoutingHandlerAdvise;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public class DatabaseRouter implements DatabasesRoutingHandlerAdvise {

    @Override
    public String routerHandler(String sql) {
        if (sql.contains("user")) {
            return "database-a";
        } else {
            return "database-b";
        }
    }

    @Override
    public String rewriteSql(String sql) {
        return sql;
    }

}
