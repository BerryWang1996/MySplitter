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

class MySplitterDatabaseManager {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDatabaseManager.class);

    private final DatabasesRoutingHandlerAdvise databaseRoutingHandler;

    MySplitterDatabaseManager(MySplitterDataSource router) {
        LOGGER.debug("MySplitterDatabaseManager is initializing.");
        Map<String, MySplitterDataBaseConfig> dbs = router.getMySplitterConfig().getMysplitter().getDatabases();
        try {
            LOGGER.debug("MySplitterDatabaseManager find {} database{} in mysplitter.yml.",
                    dbs.size(), dbs.size() > 1 ? "s" : "");
            if (dbs.size() > 1) {
                String routerClz = router.getMySplitterConfig().getMysplitter().getDatabasesRoutingHandler();
                this.databaseRoutingHandler =
                        ClassLoaderUtil.getInstance(routerClz, DatabasesRoutingHandlerAdvise.class);
            } else {
                final String dbKey = new ArrayList<String>(dbs.keySet()).get(0);
                this.databaseRoutingHandler = new DatabasesRoutingHandlerAdvise() {
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
            throw new IllegalStateException("Failed to initialize DatabasesRoutingHandler.", e);
        }
    }

    public String routerHandler(String sql) {
        return this.databaseRoutingHandler.routerHandler(sql);
    }

    public String rewriteSql(String sql) {
        return this.databaseRoutingHandler.rewriteSql(sql);
    }
}
