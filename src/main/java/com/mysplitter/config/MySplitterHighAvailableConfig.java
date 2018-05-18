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
    private String healthyHeartbeatRate;
    private String illHeartbeatRate;
    private String illAlertHandler;

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

    public String getHealthyHeartbeatRate() {
        return healthyHeartbeatRate;
    }

    public void setHealthyHeartbeatRate(String healthyHeartbeatRate) {
        this.healthyHeartbeatRate = healthyHeartbeatRate;
    }

    public String getIllHeartbeatRate() {
        return illHeartbeatRate;
    }

    public void setIllHeartbeatRate(String illHeartbeatRate) {
        this.illHeartbeatRate = illHeartbeatRate;
    }

    public String getIllAlertHandler() {
        return illAlertHandler;
    }

    public void setIllAlertHandler(String illAlertHandler) {
        this.illAlertHandler = illAlertHandler;
    }

    @Override
    public String toString() {
        return "MySplitterHighAvailableConfig{" +
                "enabled=" + enabled +
                ", lazyLoad=" + lazyLoad +
                ", detectionSql='" + detectionSql + '\'' +
                ", switchOpportunity='" + switchOpportunity + '\'' +
                ", healthyHeartbeatRate='" + healthyHeartbeatRate + '\'' +
                ", illHeartbeatRate='" + illHeartbeatRate + '\'' +
                ", illAlertHandler='" + illAlertHandler + '\'' +
                '}';
    }
}

