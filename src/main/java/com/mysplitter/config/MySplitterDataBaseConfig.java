package com.mysplitter.config;

import java.util.Map;

/**
 * 数据源配置对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:40
 */
public class MySplitterDataBaseConfig {

    private String dataSourceClass;

    private Map<String, MySplitterHighAvailableConfig> highAvailable;

    private Map<String, MySplitterLoadBalanceConfig> loadBalance;

    private Map<String, MySplitterDataSourceNodeConfig> integrates;

    private Map<String, MySplitterDataSourceNodeConfig> readers;

    private Map<String, MySplitterDataSourceNodeConfig> writers;

    public String getDataSourceClass() {
        return dataSourceClass;
    }

    public void setDataSourceClass(String dataSourceClass) {
        this.dataSourceClass = dataSourceClass;
    }

    public Map<String, MySplitterHighAvailableConfig> getHighAvailable() {
        return highAvailable;
    }

    public void setHighAvailable(Map<String, MySplitterHighAvailableConfig> highAvailable) {
        this.highAvailable = highAvailable;
    }

    public Map<String, MySplitterLoadBalanceConfig> getLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(Map<String, MySplitterLoadBalanceConfig> loadBalance) {
        this.loadBalance = loadBalance;
    }

    public Map<String, MySplitterDataSourceNodeConfig> getIntegrates() {
        return integrates;
    }

    public void setIntegrates(Map<String, MySplitterDataSourceNodeConfig> integrates) {
        this.integrates = integrates;
    }

    public Map<String, MySplitterDataSourceNodeConfig> getReaders() {
        return readers;
    }

    public void setReaders(Map<String, MySplitterDataSourceNodeConfig> readers) {
        this.readers = readers;
    }

    public Map<String, MySplitterDataSourceNodeConfig> getWriters() {
        return writers;
    }

    public void setWriters(Map<String, MySplitterDataSourceNodeConfig> writers) {
        this.writers = writers;
    }

    @Override
    public String toString() {
        return "MySplitterDataBaseConfig{" +
                "dataSourceClass='" + dataSourceClass + '\'' +
                ", highAvailable=" + highAvailable +
                ", loadBalance=" + loadBalance +
                ", integrates=" + integrates +
                ", readers=" + readers +
                ", writers=" + writers +
                '}';
    }
}
