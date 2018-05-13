package com.mysplitter.config;

import java.util.Map;

/**
 * 读数据源配置对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:45
 */
public class MySplitterReaderConfig {

    private String name;

    private String datasourceClass;

    private Integer weight;

    private Map<String, Object> configuration;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDatasourceClass() {
        return datasourceClass;
    }

    public void setDatasourceClass(String datasourceClass) {
        this.datasourceClass = datasourceClass;
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
                "name='" + name + '\'' +
                ", datasourceClass='" + datasourceClass + '\'' +
                ", weight=" + weight +
                ", configuration=" + configuration +
                '}';
    }
}
