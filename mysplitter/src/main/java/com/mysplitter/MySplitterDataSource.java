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

import com.mysplitter.config.MySplitterRootConfig;
import com.mysplitter.exceptions.MySplitterInitException;
import com.mysplitter.util.ConfigurationUtil;
import com.mysplitter.util.StringUtil;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class MySplitterDataSource implements DataSource, Serializable {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDataSource.class);

    private static final String DEFAULT_CONFIGURATION_FILE_NAME = "mysplitter.yml";

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private volatile MySplitterDataSourceManager dataSourceManager;

    private volatile MySplitterRootConfig mySplitterConfig;

    private volatile String configurationFileName;

    private volatile PrintWriter logWriter;

    public MySplitterDataSource() {
    }

    public MySplitterDataSource(String configurationFileName) {
        this.configurationFileName = configurationFileName;
    }

    public MySplitterDataSource(MySplitterRootConfig mySplitterConfig) {
        this.mySplitterConfig = mySplitterConfig;
    }

    public synchronized void init() {
        if (!isInitialized.compareAndSet(false, true)) {
            return;
        }
        try {
            LOGGER.info("MySplitter is initializing.");
            if (mySplitterConfig == null) {
                if (StringUtil.isBlank(configurationFileName)) {
                    InputStream resource = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream(DEFAULT_CONFIGURATION_FILE_NAME);
                    LOGGER.info("MySplitter is reading configuration resource named {}.",
                            DEFAULT_CONFIGURATION_FILE_NAME);
                    mySplitterConfig = ConfigurationUtil.getMySplitterConfig(resource, DEFAULT_CONFIGURATION_FILE_NAME);
                } else {
                    LOGGER.info("MySplitter is reading configuration file named {}.", configurationFileName);
                    try (InputStream resource = new FileInputStream(configurationFileName)) {
                        mySplitterConfig = ConfigurationUtil.getMySplitterConfig(resource, configurationFileName);
                    }
                }
            }
            ConfigurationUtil.checkMySplitterConfig(mySplitterConfig);
            dataSourceManager = new MySplitterDataSourceManager(this);
            LOGGER.info("MySplitter has been initialized successful.");
        } catch (Exception e) {
            dataSourceManager = null;
            isInitialized.set(false);
            throw new MySplitterInitException(e);
        }
    }

    public synchronized void close() {
        RuntimeException closeException = null;
        try {
            if (isInitialized.get() && dataSourceManager != null) {
                dataSourceManager.close();
            }
        } catch (Exception e) {
            LOGGER.error("MySplitter close failed.", e);
            closeException = new MySplitterInitException("MySplitter close failed!", e);
        } finally {
            dataSourceManager = null;
            isInitialized.set(false);
        }
        if (closeException != null) {
            throw closeException;
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        init();
        return dataSourceManager.getConnectionProxy();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        init();
        return dataSourceManager.getConnectionProxy(username, password);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface != null && iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("MySplitterDataSource does not implement " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface != null && iface.isInstance(this);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        this.logWriter = out;
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    public MySplitterRootConfig getMySplitterConfig() {
        return mySplitterConfig;
    }

    public Map<String, Object> getStatus() {
        if (!isInitialized.get() || dataSourceManager == null) {
            return Collections.emptyMap();
        }
        return dataSourceManager.getStatus();
    }
}
