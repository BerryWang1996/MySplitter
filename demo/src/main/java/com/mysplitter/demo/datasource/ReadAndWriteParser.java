package com.mysplitter.demo.datasource;


import com.mysplitter.advise.ReadAndWriteParserAdvise;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public class ReadAndWriteParser implements ReadAndWriteParserAdvise {

    @Override
    public String parseOperation(String sql) {
        return sql.toLowerCase().startsWith("select") ? "readers" : "writers";
    }

}
