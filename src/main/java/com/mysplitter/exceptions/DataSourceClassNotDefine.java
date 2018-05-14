package com.mysplitter.exceptions;

/**
 * 数据源未定义异常
 *
 * @Author: 王伯瑞
 * @Date: 2018/5/14 10:19
 */
public class DataSourceClassNotDefine extends Exception {

    private String readerOrWriterName;

    private String databaseName;

    public DataSourceClassNotDefine(String databaseName, String readerOrWriterName) {
        super("Datasource class is not define in " + databaseName + " - " + databaseName + "! Please check your " +
                "configuration !");
    }
}
