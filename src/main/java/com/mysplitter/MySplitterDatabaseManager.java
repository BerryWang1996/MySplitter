package com.mysplitter;

import com.mysplitter.advise.MySplitterDatabasesRoutingHandlerAdvise;
import com.mysplitter.config.MySplitterDataBaseConfig;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Map;

/**
 * MySplitter数据库管理器，用于管理多个数据库
 *
 * @Author: wangbor
 * @Date: 2018/5/18 8:59
 */
class MySplitterDatabaseManager {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDatabaseManager.class);

    private MySplitterDatabasesRoutingHandlerAdvise databaseRoutingHandler;

    MySplitterDatabaseManager(MySplitterDataSource router) {
        LOGGER.debug("MySplitterDatabaseManager is initializing.");
        // 如果有多个数据库，根据创建用户实现的多数据库路由，否则创建默认的数据库路由
        Map<String, MySplitterDataBaseConfig> dbs = router.getMySplitterConfig().getMysplitter()
                .getDatabases();
        try {
            LOGGER.debug("MySplitterDatabaseManager find {} database{} in mysplitter.yml.", dbs.size(), dbs.size()
                    > 1 ? "s" : "");
            if (dbs.size() > 1) {
                LOGGER.debug("MySplitterDatabaseManager is activating DatabasesRoutingHandler.");
                String routerClz = router.getMySplitterConfig().getMysplitter().getDatabasesRoutingHandler();
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Class aClass = classLoader.loadClass(routerClz);
                Constructor constructor = aClass.getConstructor();
                databaseRoutingHandler = (MySplitterDatabasesRoutingHandlerAdvise) constructor.newInstance();
            } else {
                final String dbKey = new ArrayList<String>(dbs.keySet()).get(0);
                databaseRoutingHandler = new MySplitterDatabasesRoutingHandlerAdvise() {
                    @Override
                    public String routerHandler(String sql) {
                        return dbKey;
                    }

                    @Override
                    public String rewriteSql(String sql) {
                        return sql;
                    }
                };
            }
        } catch (Exception e) {
            LOGGER.debug("MySplitterDatabaseManager activated DatabasesRoutingHandler failed!", e);
        }
    }

    public String routerHandler(String sql) {
        return this.databaseRoutingHandler.routerHandler(sql);
    }

    public String rewriteSql(String sql) {
        return this.databaseRoutingHandler.rewriteSql(sql);
    }

}
