package com.mysplitter.config;

import java.util.Map;

/**
 * 数据源配置对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:40
 */
public class MySplitterDataBaseConfig {

    private String datasourceClass;

    private Map<String, MySplitterHighAvailableConfig> highAvailable;

    private Map<String, MySplitterLoadBalanceConfig> loadBalance;

    private Map<String, MySplitterDatasourceNodeConfig> integrates;

    private Map<String, MySplitterDatasourceNodeConfig> readers;

    private Map<String, MySplitterDatasourceNodeConfig> writers;

    public String getDatasourceClass() {
        return datasourceClass;
    }

    public void setDatasourceClass(String datasourceClass) {
        this.datasourceClass = datasourceClass;
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

    public Map<String, MySplitterDatasourceNodeConfig> getIntegrates() {
        return integrates;
    }

    public void setIntegrates(Map<String, MySplitterDatasourceNodeConfig> integrates) {
        this.integrates = integrates;
    }

    public Map<String, MySplitterDatasourceNodeConfig> getReaders() {
        return readers;
    }

    public void setReaders(Map<String, MySplitterDatasourceNodeConfig> readers) {
        this.readers = readers;
    }

    public Map<String, MySplitterDatasourceNodeConfig> getWriters() {
        return writers;
    }

    public void setWriters(Map<String, MySplitterDatasourceNodeConfig> writers) {
        this.writers = writers;
    }

    @Override
    public String toString() {
        return "MySplitterDataBaseConfig{" +
                "datasourceClass='" + datasourceClass + '\'' +
                ", highAvailable=" + highAvailable +
                ", loadBalance=" + loadBalance +
                ", integrates=" + integrates +
                ", readers=" + readers +
                ", writers=" + writers +
                '}';
    }
}
