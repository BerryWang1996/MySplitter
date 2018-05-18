package com.mysplitter.config;

/**
 * 高可用配置
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:22
 */
public class MySplitterHighAvailableConfig {

    private boolean enabled;
    private boolean lazyLoad;
    private String detectionSql;
    private String switchOpportunity;
    private String alivedHeartbeatRate;
    private String diedHeartbeatRate;
    private String diedAlertHandler;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLazyLoad() {
        return lazyLoad;
    }

    public void setLazyLoad(boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    public String getDetectionSql() {
        return detectionSql;
    }

    public void setDetectionSql(String detectionSql) {
        this.detectionSql = detectionSql;
    }

    public String getSwitchOpportunity() {
        return switchOpportunity;
    }

    public void setSwitchOpportunity(String switchOpportunity) {
        this.switchOpportunity = switchOpportunity;
    }

    public String getAlivedHeartbeatRate() {
        return alivedHeartbeatRate;
    }

    public void setAlivedHeartbeatRate(String alivedHeartbeatRate) {
        this.alivedHeartbeatRate = alivedHeartbeatRate;
    }

    public String getDiedHeartbeatRate() {
        return diedHeartbeatRate;
    }

    public void setDiedHeartbeatRate(String diedHeartbeatRate) {
        this.diedHeartbeatRate = diedHeartbeatRate;
    }

    public String getDiedAlertHandler() {
        return diedAlertHandler;
    }

    public void setDiedAlertHandler(String diedAlertHandler) {
        this.diedAlertHandler = diedAlertHandler;
    }

    @Override
    public String toString() {
        return "MySplitterHighAvailableConfig{" +
                "enabled=" + enabled +
                ", lazyLoad=" + lazyLoad +
                ", detectionSql='" + detectionSql + '\'' +
                ", switchOpportunity='" + switchOpportunity + '\'' +
                ", alivedHeartbeatRate='" + alivedHeartbeatRate + '\'' +
                ", diedHeartbeatRate='" + diedHeartbeatRate + '\'' +
                ", diedAlertHandler='" + diedAlertHandler + '\'' +
                '}';
    }
}

