package com.mysplitter;

import com.mysplitter.advise.MySplitterDataSourceIllAlerterAdvise;
import org.slf4j.LoggerFactory;

public class MyDataSourceIllAlertHandler implements MySplitterDataSourceIllAlerterAdvise {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MyDataSourceIllAlertHandler.class);

    @Override
    public void illAlerter(String databaseName, String nodeName, Exception e) {
        LOGGER.error("database:{}, node:{}", databaseName, nodeName, e);
    }
}