package com.mysplitter.advise;

/**
 * 多数据库路由处理器接口类
 */
public interface MySplitterDatabasesRoutingHandlerAdvise {

    /**
     * @return database name (in mysplitter.yml)
     */
    String routerHandler(String sql);

    /**
     * @return new sql
     */
    String rewriteSql(String sql);

}
