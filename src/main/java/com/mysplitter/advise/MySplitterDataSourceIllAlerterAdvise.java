package com.mysplitter.advise;

/**
 * 数据源异常警告处理器接口类
 */
public interface MySplitterDataSourceIllAlerterAdvise {

    void illAlerter(String databaseName, String nodeName, Exception e);

}
