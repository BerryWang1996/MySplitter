package com.mysplitter.demo.datasource;

import com.mysplitter.advise.DataSourceIllAlerterAdvise;
import lombok.extern.slf4j.Slf4j;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
@Slf4j
public class DataSourceIllAlertHandler implements DataSourceIllAlerterAdvise {

    @Override
    public void illAlerter(String s, String s1, Exception e) {
        log.warn("Datasource got error.", e);
    }

}
