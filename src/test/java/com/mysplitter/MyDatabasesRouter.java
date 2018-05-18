package com.mysplitter;

import com.mysplitter.advise.MySplitterDatabasesRoutingHandlerAdvise;

public class MyDatabasesRouter implements MySplitterDatabasesRoutingHandlerAdvise {

    @Override
    public String routingHandler(String sql) {
        if (sql.contains("bbb")) {
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