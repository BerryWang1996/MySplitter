package com.mysplitter.config;

/**
 * 配置文件根对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 19:20
 */
public class MySplitterRootConfig {

    private MySplitterConfig mysplitter;

    public MySplitterConfig getMysplitter() {
        return mysplitter;
    }

    public void setMysplitter(MySplitterConfig mysplitter) {
        this.mysplitter = mysplitter;
    }

    @Override
    public String toString() {
        return "MySplitterRootConfig{" +
                "mysplitter=" + mysplitter +
                '}';
    }
}
