package com.mysplitter.config;

/**
 * 高可用配置
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:22
 */
public class MySplitterHighAvailableConfig {

    public static final String DEFAULT_DETECTION_SQL = "SELECT 1";

    public static final String DEFAULT_HEARTBEAT_RATE = "1s";

    private boolean enableLazyLoadingDataSource;

    private String switchOpportunity;

    private String heartbeatRate;

    private String detectionSql;

    private String diedAlertHandler;

    public boolean isEnableLazyLoadingDataSource() {
        return enableLazyLoadingDataSource;
    }

    public void setEnableLazyLoadingDataSource(boolean enableLazyLoadingDataSource) {
        this.enableLazyLoadingDataSource = enableLazyLoadingDataSource;
    }

    public String getSwitchOpportunity() {
        return switchOpportunity;
    }

    public void setSwitchOpportunity(String switchOpportunity) {
        this.switchOpportunity = switchOpportunity;
    }

    public String getHeartbeatRate() {
        return heartbeatRate;
    }

    public void setHeartbeatRate(String heartbeatRate) {
        this.heartbeatRate = heartbeatRate;
    }

    public String getDetectionSql() {
        return detectionSql;
    }

    public void setDetectionSql(String detectionSql) {
        this.detectionSql = detectionSql;
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
                "enableLazyLoadingDataSource=" + enableLazyLoadingDataSource +
                ", switchOpportunity='" + switchOpportunity + '\'' +
                ", heartbeatRate='" + heartbeatRate + '\'' +
                ", detectionSql='" + detectionSql + '\'' +
                ", diedAlertHandler='" + diedAlertHandler + '\'' +
                '}';
    }
}

