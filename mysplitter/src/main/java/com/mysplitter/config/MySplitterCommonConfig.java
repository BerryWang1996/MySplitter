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

import java.util.Map;

/**
 * 通用配置对象
 */
public class MySplitterCommonConfig {

    private String dataSourceClass;

    private Map<String, MySplitterHighAvailableConfig> highAvailable;

    private Map<String, MySplitterLoadBalanceConfig> loadBalance;

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

    @Override
    public String toString() {
        return "MySplitterCommonConfig{" +
                "dataSourceClass='" + dataSourceClass + '\'' +
                ", highAvailable=" + highAvailable +
                ", loadBalance=" + loadBalance +
                '}';
    }
}
