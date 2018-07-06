package com.mysplitter;

import com.mysplitter.advise.MySplitterDataSourceIllAlerterAdvise;
import org.slf4j.LoggerFactory;

/**
 * 默认的数据源异常提醒
 */
public class DefaultDataSourceIllAlertHandler implements MySplitterDataSourceIllAlerterAdvise {

    private static final org.slf4j.Logger LOGGER =
            LoggerFactory.getLogger(DefaultDataSourceIllAlertHandler.class);

    @Override
    public void illAlerter(String databaseName, String nodeName, Exception e) {
        LOGGER.error("database:{}, node:{} is ill", databaseName, nodeName, e);
    }
}