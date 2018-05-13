package com.mysplitter.config;

/**
 * 日志配置对象
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:22
 */
public class MySplitterLogConfig {

    private boolean enabled;

    private String level;

    private boolean showSql;

    private boolean showSqlPretty;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

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
                "enabled=" + enabled +
                ", level='" + level + '\'' +
                ", showSql=" + showSql +
                ", showSqlPretty=" + showSqlPretty +
                '}';
    }
}
