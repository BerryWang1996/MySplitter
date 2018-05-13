package com.mysplitter.config;

import java.util.Map;

/**
 * 数据源配置对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:40
 */
public class MySplitterDataBaseConfig {

    private String name;

    private String datasourceClass;

    private MySplitterHighAvailableConfig highAvailable;

    private Map<String, MySplitterLoadBalanceConfig> loadBalance;

    private Map<String, MySplitterReaderConfig> readers;

    private Map<String, MySplitterWriterConfig> writers;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDatasourceClass() {
        return datasourceClass;
    }

    public void setDatasourceClass(String datasourceClass) {
        this.datasourceClass = datasourceClass;
    }

    public MySplitterHighAvailableConfig getHighAvailable() {
        return highAvailable;
    }

    public void setHighAvailable(MySplitterHighAvailableConfig highAvailable) {
        this.highAvailable = highAvailable;
    }

    public Map<String, MySplitterLoadBalanceConfig> getLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(Map<String, MySplitterLoadBalanceConfig> loadBalance) {
        this.loadBalance = loadBalance;
    }

    public Map<String, MySplitterReaderConfig> getReaders() {
        return readers;
    }

    public void setReaders(Map<String, MySplitterReaderConfig> readers) {
        this.readers = readers;
    }

    public Map<String, MySplitterWriterConfig> getWriters() {
        return writers;
    }

    public void setWriters(Map<String, MySplitterWriterConfig> writers) {
        this.writers = writers;
    }

    @Override
    public String toString() {
        return "MySplitterDataBaseConfig{" +
                "name='" + name + '\'' +
                ", datasourceClass='" + datasourceClass + '\'' +
                ", highAvailable=" + highAvailable +
                ", loadBalance=" + loadBalance +
                ", readers=" + readers +
                ", writers=" + writers +
                '}';
    }
}
