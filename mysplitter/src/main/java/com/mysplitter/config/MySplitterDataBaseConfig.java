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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源配置对象
 */
public class MySplitterDataBaseConfig {

    private String dataSourceClass;

    private Map<String, MySplitterLoadBalanceConfig> loadBalance;

    private LinkedHashMap<String, MySplitterDataSourceNodeConfig> integrates;

    private LinkedHashMap<String, MySplitterDataSourceNodeConfig> readers;

    private LinkedHashMap<String, MySplitterDataSourceNodeConfig> writers;

    public String getDataSourceClass() {
        return dataSourceClass;
    }

    public void setDataSourceClass(String dataSourceClass) {
        this.dataSourceClass = dataSourceClass;
    }

    public Map<String, MySplitterLoadBalanceConfig> getLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(Map<String, MySplitterLoadBalanceConfig> loadBalance) {
        this.loadBalance = loadBalance;
    }

    public LinkedHashMap<String, MySplitterDataSourceNodeConfig> getIntegrates() {
        return integrates;
    }

    public void setIntegrates(LinkedHashMap<String, MySplitterDataSourceNodeConfig> integrates) {
        this.integrates = integrates;
    }

    public LinkedHashMap<String, MySplitterDataSourceNodeConfig> getReaders() {
        return readers;
    }

    public void setReaders(LinkedHashMap<String, MySplitterDataSourceNodeConfig> readers) {
        this.readers = readers;
    }

    public LinkedHashMap<String, MySplitterDataSourceNodeConfig> getWriters() {
        return writers;
    }

    public void setWriters(LinkedHashMap<String, MySplitterDataSourceNodeConfig> writers) {
        this.writers = writers;
    }

    @Override
    public String toString() {
        return "MySplitterDataBaseConfig{" +
                "dataSourceClass='" + dataSourceClass + '\'' +
                ", loadBalance=" + loadBalance +
                ", integrates=" + integrates +
                ", readers=" + readers +
                ", writers=" + writers +
                '}';
    }
}
