package com.mysplitter;

import com.mysplitter.advise.DataSourceFilterAdvise;

public class MyDataSourceFilters1 implements DataSourceFilterAdvise {

    @Override
    public void doFilter(String databaseName, String nodeName, String sql) throws Exception {

    }
}