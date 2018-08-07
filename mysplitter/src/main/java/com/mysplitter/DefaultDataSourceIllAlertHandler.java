/*
 * Copyright 2018 BerryWang1996
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mysplitter;

import com.mysplitter.advise.MySplitterDataSourceIllAlerterAdvise;
import org.slf4j.LoggerFactory;

/**
 * 默认的数据源异常提醒
 */
public class DefaultDataSourceIllAlertHandler implements MySplitterDataSourceIllAlerterAdvise {

    private static final org.slf4j.Logger LOGGER =
            LoggerFactory.getLogger(DefaultDataSourceIllAlertHandler.class);

    @Override
    public void illAlerter(String databaseName, String nodeName, Exception e) {
        LOGGER.error("database:{}, node:{} is ill", databaseName, nodeName, e);
    }
}