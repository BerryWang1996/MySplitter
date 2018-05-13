package com.mysplitter.config;

import java.io.Serializable;
import java.util.Map;

/**
 * 配置文件对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:17
 */
public class MySplitterConfig implements Serializable {

    private MySplitterCommonConfig common;

    private Map<String, MySplitterDataBaseConfig> databases;

    private MySplitterDatabaseRoutingConfig databasesRouting;

    private MySplitterLogConfig log;

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

    public MySplitterDatabaseRoutingConfig getDatabasesRouting() {
        return databasesRouting;
    }

    public void setDatabasesRouting(MySplitterDatabaseRoutingConfig databasesRouting) {
        this.databasesRouting = databasesRouting;
    }

    public MySplitterLogConfig getLog() {
        return log;
    }

    public void setLog(MySplitterLogConfig log) {
        this.log = log;
    }

    @Override
    public String toString() {
        return "MySplitterConfig{" +
                "common=" + common +
                ", databases=" + databases +
                ", databasesRouting=" + databasesRouting +
                ", log=" + log +
                '}';
    }
}