package com.mysplitter.demo.datasource;

import com.mysplitter.advise.DataSourceIllAlerterAdvise;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public class DataSourceIllAlertHandler implements DataSourceIllAlerterAdvise {

    @Override
    public void alert(String s, String s1, Exception e) {
        // Demo hook intentionally left blank.
    }

}
