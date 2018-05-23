package com.mysplitter.wrapper;

import com.mysplitter.config.MySplitterDataSourceNodeConfig;

import javax.sql.DataSource;

/**
 * @Author: wangbor
 * @Date: 2018/5/18 9:49
 */
public class DataSourceWrapper {

    public DataSourceWrapper(String nodeName, String dataBaseName, MySplitterDataSourceNodeConfig
            mySplitterDataSourceNodeConfig) {
        this.nodeName = nodeName;
        this.dataBaseName = dataBaseName;
        this.mySplitterDataSourceNodeConfig = mySplitterDataSourceNodeConfig;
    }

    private DataSource realDataSource;

    private String nodeName;

    private String dataBaseName;

    private MySplitterDataSourceNodeConfig mySplitterDataSourceNodeConfig;

    public DataSource getRealDataSource() {
        return realDataSource;
    }

    public void setRealDataSource(DataSource realDataSource) {
        this.realDataSource = realDataSource;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getDataBaseName() {
        return dataBaseName;
    }

    public MySplitterDataSourceNodeConfig getMySplitterDataSourceNodeConfig() {
        return mySplitterDataSourceNodeConfig;
    }
}
