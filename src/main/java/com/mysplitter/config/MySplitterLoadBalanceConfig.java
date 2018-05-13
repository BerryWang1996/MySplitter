package com.mysplitter.config;

/**
 * 负载均衡配置
 *
 * @Author: wangbor
 * @Date: 2018/5/13 21:20
 */
public class MySplitterLoadBalanceConfig {

    private boolean enabled;

    private String strategy;

    private String datasourceName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getDatasourceName() {
        return datasourceName;
    }

    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }

    @Override
    public String toString() {
        return "MySplitterLoadBalanceConfig{" +
                "enabled=" + enabled +
                ", strategy='" + strategy + '\'' +
                ", datasourceName='" + datasourceName + '\'' +
                '}';
    }
}
