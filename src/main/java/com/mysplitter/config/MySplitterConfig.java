package com.mysplitter.config;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 配置文件对象
 */
public class MySplitterConfig implements Serializable {

    private String databasesRoutingHandler;

    private String readAndWriteParser;

    private boolean enablePasswordEncryption;

    private List<String> filters;

    private MySplitterCommonConfig common;

    private Map<String, MySplitterDataBaseConfig> databases;

    public String getDatabasesRoutingHandler() {
        return databasesRoutingHandler;
    }

    public void setDatabasesRoutingHandler(String databasesRoutingHandler) {
        this.databasesRoutingHandler = databasesRoutingHandler;
    }

    public String getReadAndWriteParser() {
        return readAndWriteParser;
    }

    public void setReadAndWriteParser(String readAndWriteParser) {
        this.readAndWriteParser = readAndWriteParser;
    }

    public boolean isEnablePasswordEncryption() {
        return enablePasswordEncryption;
    }

    public void setEnablePasswordEncryption(boolean enablePasswordEncryption) {
        this.enablePasswordEncryption = enablePasswordEncryption;
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
                ", readAndWriteParser='" + readAndWriteParser + '\'' +
                ", filters=" + filters +
                ", common=" + common +
                ", databases=" + databases +
                '}';
    }
}