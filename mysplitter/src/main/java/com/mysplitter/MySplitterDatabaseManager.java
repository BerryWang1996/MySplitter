/*
 * Copyright 2018 BerryWang1996
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mysplitter;

import com.mysplitter.advise.DatabasesRoutingHandlerAdvise;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.util.ClassLoaderUtil;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

/**
 * MySplitter数据库管理器，用于管理多个数据库
 */
class MySplitterDatabaseManager {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDatabaseManager.class);

    private DatabasesRoutingHandlerAdvise databaseRoutingHandler;

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
                databaseRoutingHandler =
                        ClassLoaderUtil.getInstance(routerClz, DatabasesRoutingHandlerAdvise.class);
            } else {
                LOGGER.debug("MySplitterDatabaseManager is activating default DatabasesRoutingHandler, " +
                        "Caused only one database.");
                final String dbKey = new ArrayList<String>(dbs.keySet()).get(0);
                databaseRoutingHandler = new DatabasesRoutingHandlerAdvise() {
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

    /**
     * 路由处理
     */
    public String routerHandler(String sql) {
        return this.databaseRoutingHandler.routerHandler(sql);
    }

    /**
     * 路由重写sql
     */
    public String rewriteSql(String sql) {
        // TODO 重写sql功能没有用到
        return this.databaseRoutingHandler.rewriteSql(sql);
    }

}
