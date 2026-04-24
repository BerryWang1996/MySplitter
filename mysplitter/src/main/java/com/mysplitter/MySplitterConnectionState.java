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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class MySplitterConnectionState {

    private boolean autoCommit = true;

    private boolean readOnly;

    private String catalog;

    private boolean catalogConfigured;

    private Integer transactionIsolation;

    private Map<String, Class<?>> typeMap;

    private boolean typeMapConfigured;

    private Integer holdability;

    private final Properties clientInfo = new Properties();

    private boolean clientInfoConfigured;

    private String schema;

    private boolean schemaConfigured;

    private Executor networkTimeoutExecutor;

    private Integer networkTimeoutMilliseconds;

    public synchronized boolean isAutoCommit() {
        return autoCommit;
    }

    public synchronized void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public synchronized boolean isReadOnly() {
        return readOnly;
    }

    public synchronized void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public synchronized String getCatalog() {
        return catalog;
    }

    public synchronized boolean hasCatalog() {
        return catalogConfigured;
    }

    public synchronized void setCatalog(String catalog) {
        this.catalog = catalog;
        this.catalogConfigured = true;
    }

    public synchronized Integer getTransactionIsolation() {
        return transactionIsolation;
    }

    public synchronized void setTransactionIsolation(Integer transactionIsolation) {
        this.transactionIsolation = transactionIsolation;
    }

    public synchronized Map<String, Class<?>> getTypeMap() {
        if (typeMap == null) {
            return null;
        }
        return new HashMap<String, Class<?>>(typeMap);
    }

    public synchronized boolean hasTypeMap() {
        return typeMapConfigured;
    }

    public synchronized void setTypeMap(Map<String, Class<?>> typeMap) {
        if (typeMap == null) {
            this.typeMap = null;
        } else {
            this.typeMap = new HashMap<String, Class<?>>(typeMap);
        }
        this.typeMapConfigured = true;
    }

    public synchronized Integer getHoldability() {
        return holdability;
    }

    public synchronized void setHoldability(Integer holdability) {
        this.holdability = holdability;
    }

    public synchronized void setClientInfo(String name, String value) {
        if (name == null) {
            return;
        }
        if (value == null) {
            clientInfo.remove(name);
        } else {
            clientInfo.setProperty(name, value);
        }
        clientInfoConfigured = true;
    }

    public synchronized void setClientInfo(Properties properties) {
        clientInfo.clear();
        if (properties != null) {
            clientInfo.putAll(properties);
        }
        clientInfoConfigured = true;
    }

    public synchronized String getClientInfo(String name) {
        return clientInfo.getProperty(name);
    }

    public synchronized Properties getClientInfo() {
        Properties properties = new Properties();
        properties.putAll(clientInfo);
        return properties;
    }

    public synchronized boolean hasClientInfo() {
        return clientInfoConfigured;
    }

    public synchronized String getSchema() {
        return schema;
    }

    public synchronized boolean hasSchema() {
        return schemaConfigured;
    }

    public synchronized void setSchema(String schema) {
        this.schema = schema;
        this.schemaConfigured = true;
    }

    public synchronized Executor getNetworkTimeoutExecutor() {
        return networkTimeoutExecutor;
    }

    public synchronized Integer getNetworkTimeoutMilliseconds() {
        return networkTimeoutMilliseconds;
    }

    public synchronized void setNetworkTimeout(Executor executor, Integer milliseconds) {
        this.networkTimeoutExecutor = executor;
        this.networkTimeoutMilliseconds = milliseconds;
    }

    public synchronized void apply(Connection connection) throws SQLException {
        connection.setAutoCommit(autoCommit);
        connection.setReadOnly(readOnly);
        if (catalogConfigured) {
            connection.setCatalog(catalog);
        }
        if (transactionIsolation != null) {
            connection.setTransactionIsolation(transactionIsolation.intValue());
        }
        if (typeMapConfigured) {
            connection.setTypeMap(typeMap == null ? null : new HashMap<String, Class<?>>(typeMap));
        }
        if (holdability != null) {
            connection.setHoldability(holdability.intValue());
        }
        if (clientInfoConfigured) {
            Properties properties = new Properties();
            properties.putAll(clientInfo);
            connection.setClientInfo(properties);
        }
        if (schemaConfigured) {
            connection.setSchema(schema);
        }
        if (networkTimeoutExecutor != null && networkTimeoutMilliseconds != null) {
            connection.setNetworkTimeout(networkTimeoutExecutor, networkTimeoutMilliseconds.intValue());
        }
    }
}
