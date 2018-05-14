package com.mysplitter.config;

/**
 * 日志配置对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:22
 */
public class MySplitterLogConfig {

    private boolean showSql;

    private boolean showSqlPretty;

    public boolean isShowSql() {
        return showSql;
    }

    public void setShowSql(boolean showSql) {
        this.showSql = showSql;
    }

    public boolean isShowSqlPretty() {
        return showSqlPretty;
    }

    public void setShowSqlPretty(boolean showSqlPretty) {
        this.showSqlPretty = showSqlPretty;
    }

    @Override
    public String toString() {
        return "MySplitterLogConfig{" +
                "showSql=" + showSql +
                ", showSqlPretty=" + showSqlPretty +
                '}';
    }
}
