package com.mysplitter;

import com.mysplitter.advise.MySplitterReadAndWriteParserAdvise;

/**
 * 默认的读写解析器
 */
public class DefaultReadAndWriteParser implements MySplitterReadAndWriteParserAdvise {

    /**
     * 解析sql是读操作还是写操作
     *
     * @param sql 操作的sql
     * @return readers:是读操作 writers:是写操作
     */
    @Override
    public String parseOperation(String sql) {
        if (sql.startsWith("SELECT") || sql.startsWith("select")) {
            return "readers";
        }
        return "writers";
    }

}