package com.mysplitter.config;

/**
 * 负载均衡配置
 */
public class MySplitterLoadBalanceConfig {

    private boolean enabled;

    private String strategy;

    private String databaseName;

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

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    @Override
    public String toString() {
        return "MySplitterLoadBalanceConfig{" +
                "enabled=" + enabled +
                ", strategy='" + strategy + '\'' +
                ", databaseName='" + databaseName + '\'' +
                '}';
    }
}
