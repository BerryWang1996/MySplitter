package com.mysplitter.config;

import java.util.Map;

/**
 * 通用配置对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:23
 */
public class MySplitterCommonConfig {

    private String datasourceClass;

    private Map<String, MySplitterHighAvailableConfig> highAvailable;

    private Map<String, MySplitterLoadBalanceConfig> loadBalance;

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

    @Override
    public String toString() {
        return "MySplitterCommonConfig{" +
                "datasourceClass='" + datasourceClass + '\'' +
                ", highAvailable=" + highAvailable +
                ", loadBalance=" + loadBalance +
                '}';
    }
}
