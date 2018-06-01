package com.mysplitter.advise;

/**
 * @Author: wangbor
 * @Date: 2018/5/28 16:13
 */
public interface MySplitterReadAndWriteParserAdvise {

    /**
     * @return sql will use writer data source when return "writer" ,
     * use reader data source when return "reader", use integrates when return others.
     */
    String parseOperation(String sql);

}