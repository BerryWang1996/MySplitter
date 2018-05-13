package com.mysplitter.config;

/**
 * 多数据库路由配置类
 *
 * @Author: wangbor
 * @Date: 2018/5/13 21:40
 */
public class MySplitterDatabaseRoutingConfig {

    private String mode;

    private String routingHandler;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRoutingHandler() {
        return routingHandler;
    }

    public void setRoutingHandler(String routingHandler) {
        this.routingHandler = routingHandler;
    }

    @Override
    public String toString() {
        return "MySplitterDatabaseRoutingConfig{" +
                "mode='" + mode + '\'' +
                ", routingHandler='" + routingHandler + '\'' +
                '}';
    }
}
