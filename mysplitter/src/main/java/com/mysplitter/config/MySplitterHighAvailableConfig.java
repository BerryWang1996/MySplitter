/*
 * Copyright 2018 BerryWang1996
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mysplitter.config;

/**
 * 高可用配置
 */
public class MySplitterHighAvailableConfig {

    private boolean enabled;
    private boolean lazyLoad;
    private String detectionSql;
    private String switchOpportunity;
    private String healthyHeartbeatRate;
    private String illHeartbeatRate;

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

    @Override
    public String toString() {
        return "MySplitterHighAvailableConfig{" +
                "enabled=" + enabled +
                ", lazyLoad=" + lazyLoad +
                ", detectionSql='" + detectionSql + '\'' +
                ", switchOpportunity='" + switchOpportunity + '\'' +
                ", healthyHeartbeatRate='" + healthyHeartbeatRate + '\'' +
                ", illHeartbeatRate='" + illHeartbeatRate + '\'' +
                '}';
    }
}

