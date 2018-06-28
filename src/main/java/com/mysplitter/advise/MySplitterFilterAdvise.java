package com.mysplitter.advise;

public interface MySplitterFilterAdvise {

    void doFilter(String databaseName, String nodeName, String sql) throws Exception;

}
