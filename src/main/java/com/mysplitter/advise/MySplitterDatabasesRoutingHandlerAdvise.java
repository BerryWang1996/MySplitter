package com.mysplitter.advise;

/**
 * 多数据库路由处理器接口类
 *
 * @Author: wangbor
 * @Date: 2018/5/14 11:00
 */
public interface MySplitterDatabasesRoutingHandlerAdvise {

    /**
     * @return database name (in mysplitter.yml)
     */
    String routingHandler(String sql);

    /**
     * @return new sql
     */
    String rewriteSql(String sql);

}