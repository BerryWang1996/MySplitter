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
 * 数据源节点配置对象
 */
public class MySplitterDataSourceNodeConfig {

    private String dataSourceClass;

    private Integer weight;

    private Map<String, Object> configuration;

    public String getDataSourceClass() {
        return dataSourceClass;
    }

    public void setDataSourceClass(String dataSourceClass) {
        this.dataSourceClass = dataSourceClass;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    @Override
    public String toString() {
        return "MySplitterReaderConfig{" +
                "dataSourceClass='" + dataSourceClass + '\'' +
                ", weight=" + weight +
                ", configuration=" + configuration +
                '}';
    }
}
