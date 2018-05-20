package com.mysplitter.config;

import java.util.Map;

/**
 * 数据源节点配置对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:45
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
