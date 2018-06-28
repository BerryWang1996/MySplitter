package com.mysplitter.exceptions;

/**
 * 数据源配置未定义异常
 */
public class DataSourceConfigurationNotDefine extends Exception {

    private String readerOrWriterName;

    private String databaseName;

    public DataSourceConfigurationNotDefine(String databaseName, String readerOrWriterName) {
        super("DataSource configuration is empty in \"" + databaseName + " " + readerOrWriterName + "\"! Please" +
                " check " + "your configuration !");
    }
}
