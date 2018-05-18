package com.mysplitter.config;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 配置文件对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:17
 */
public class MySplitterConfig implements Serializable {

    private String databasesRoutingHandler;

    private List<String> filters;

    private MySplitterCommonConfig common;

    private Map<String, MySplitterDataBaseConfig> databases;

    public String getDatabasesRoutingHandler() {
        return databasesRoutingHandler;
    }

    public void setDatabasesRoutingHandler(String databasesRoutingHandler) {
        this.databasesRoutingHandler = databasesRoutingHandler;
    }

    public List<String> getFilters() {
        return filters;
    }

    public void setFilters(List<String> filters) {
        this.filters = filters;
    }

    public MySplitterCommonConfig getCommon() {
        return common;
    }

    public void setCommon(MySplitterCommonConfig common) {
        this.common = common;
    }

    public Map<String, MySplitterDataBaseConfig> getDatabases() {
        return databases;
    }

    public void setDatabases(Map<String, MySplitterDataBaseConfig> databases) {
        this.databases = databases;
    }

    @Override
    public String toString() {
        return "MySplitterConfig{" +
                "databasesRoutingHandler='" + databasesRoutingHandler + '\'' +
                ", filters=" + filters +
                ", common=" + common +
                ", databases=" + databases +
                '}';
    }
}