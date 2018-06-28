package com.mysplitter;

import com.mysplitter.advise.MySplitterReadAndWriteParserAdvise;

public class DefaultReadAndWriteParser implements MySplitterReadAndWriteParserAdvise {

    @Override
    public String parseOperation(String sql) {
        if (sql.startsWith("SELECT") || sql.startsWith("select")) {
            return "readers";
        }
        return "writers";
    }

}