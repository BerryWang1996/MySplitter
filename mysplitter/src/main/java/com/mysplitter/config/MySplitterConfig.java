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

package com.mysplitter.config;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 配置文件对象
 */
public class MySplitterConfig implements Serializable {

    private String databasesRoutingHandler;

    private String readAndWriteParser;

    private boolean enablePasswordEncryption;

    private List<String> filters;

    private MySplitterCommonConfig common;

    private Map<String, MySplitterDataBaseConfig> databases;

    public String getDatabasesRoutingHandler() {
        return databasesRoutingHandler;
    }

    public void setDatabasesRoutingHandler(String databasesRoutingHandler) {
        this.databasesRoutingHandler = databasesRoutingHandler;
    }

    public String getReadAndWriteParser() {
        return readAndWriteParser;
    }

    public void setReadAndWriteParser(String readAndWriteParser) {
        this.readAndWriteParser = readAndWriteParser;
    }

    public boolean isEnablePasswordEncryption() {
        return enablePasswordEncryption;
    }

    public void setEnablePasswordEncryption(boolean enablePasswordEncryption) {
        this.enablePasswordEncryption = enablePasswordEncryption;
    }

    public List<String> getFilters() {
        return filters;
    }

    public void setFilters(List<String> filters) {
        this.filters = filters;
    }

    public MySplitterCommonConfig getCommon() {
        return common;
    }

    public void setCommon(MySplitterCommonConfig common) {
        this.common = common;
    }

    public Map<String, MySplitterDataBaseConfig> getDatabases() {
        return databases;
    }

    public void setDatabases(Map<String, MySplitterDataBaseConfig> databases) {
        this.databases = databases;
    }

    @Override
    public String toString() {
        return "MySplitterConfig{" +
                "databasesRoutingHandler='" + databasesRoutingHandler + '\'' +
                ", readAndWriteParser='" + readAndWriteParser + '\'' +
                ", filters=" + filters +
                ", common=" + common +
                ", databases=" + databases +
                '}';
    }
}