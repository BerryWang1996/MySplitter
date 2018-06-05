package com.mysplitter;

import com.mysplitter.advise.MySplitterReadAndWriteParserAdvise;

/**
 * @Author: wangbor
 * @Date: 2018/5/14 20:59
 */
public class DefaultReadAndWriteParser implements MySplitterReadAndWriteParserAdvise {

    @Override
    public String parseOperation(String sql) {
        if (sql.startsWith("SELECT") || sql.startsWith("select")) {
            return "readers";
        }
        return "writers";
    }

}