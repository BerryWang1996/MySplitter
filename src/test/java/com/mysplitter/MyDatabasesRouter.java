package com.mysplitter;

import com.mysplitter.advise.MySplitterDatabasesRoutingHandlerAdvise;

public class MyDatabasesRouter implements MySplitterDatabasesRoutingHandlerAdvise {

    @Override
    public String routerHandler(String sql) {
        if (sql.contains("user")) {
            return "database-b";
        } else {
            return "database-a";
        }
    }

    @Override
    public String rewriteSql(String sql) {
        return sql;
    }

}