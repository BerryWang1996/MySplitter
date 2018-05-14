package com.mysplitter.exceptions;

/**
 * 数据源配置未定义异常
 *
 * @Author: wangbor
 * @Date: 2018/5/14 10:19
 */
public class DataSourceConfigurationNotDefine extends Exception {

    private String readerOrWriterName;

    private String databaseName;

    public DataSourceConfigurationNotDefine(String databaseName, String readerOrWriterName) {
        super("Datasource configuration is empty in \"" + databaseName + " " + readerOrWriterName + "\"! Please" +
                " check " + "your configuration !");
    }
}
