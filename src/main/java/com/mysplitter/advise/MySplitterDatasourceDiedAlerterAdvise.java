package com.mysplitter.advise;

/**
 * 数据源异常警告处理器接口类
 *
 * @Author: wangbor
 * @Date: 2018/5/14 11:00
 */
public interface MySplitterDatasourceDiedAlerterAdvise {

    void diedAlerter(String databaseName, String nodeName);

}
