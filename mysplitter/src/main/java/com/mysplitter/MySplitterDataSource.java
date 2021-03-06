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
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * MySplitter数据源路由，实现DataSource接口
 */
public class MySplitterDataSource implements DataSource, Serializable {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDataSource.class);

    private static final String DEFAULT_CONFIGURATION_FILE_NAME = "mysplitter.yml";

    private MySplitterDataSourceManager dataSourceManager;

    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    private MySplitterRootConfig mySplitterConfig;

    private String configurationFileName;

    public MySplitterDataSource() {
    }

    public MySplitterDataSource(String configurationFileName) {
        this.configurationFileName = configurationFileName;
    }

    public MySplitterDataSource(MySplitterRootConfig mySplitterConfig) {
        this.mySplitterConfig = mySplitterConfig;
    }

    /**
     * 初始化配置文件，以及创建连接池
     */
    public synchronized void init() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                // 获取配置文件
                LOGGER.info("MySplitter is initializing.");
                if (mySplitterConfig == null) {
                    if (StringUtil.isBlank(configurationFileName)) {
                        URL resource =
                                Thread.currentThread().getContextClassLoader().getResource(DEFAULT_CONFIGURATION_FILE_NAME);
                        configurationFileName = resource.getPath();
                    }
                    LOGGER.info("MySplitter is reading configuration file named {}.", configurationFileName);
                    mySplitterConfig = ConfigurationUtil.getMySplitterConfig(configurationFileName);
                }
                // 对配置文件进行检查
                ConfigurationUtil.checkMySplitterConfig(mySplitterConfig);
                LOGGER.info("MySplitter configuration passed.");
                // 创建数据源管理器
                dataSourceManager = new MySplitterDataSourceManager(this);
                LOGGER.info("MySplitter has been initialized successful.");
            } catch (Exception e) {
                new MySplitterInitException(e).printStackTrace();
                System.exit(1);
            }
        }
    }

    /**
     * 关闭所有的连接
     */
    public synchronized void close() {
        try {
            if (isInitialized.get()) {
                this.dataSourceManager.close();
                isInitialized.set(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        init();
        return dataSourceManager.getConnectionProxy();
    }

    public Connection getConnection(String username, String password) throws SQLException {
        init();
        return dataSourceManager.getConnectionProxy(username, password);
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    public void setLogWriter(PrintWriter out) throws SQLException {
    }

    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    public void setLoginTimeout(int seconds) throws SQLException {
        DriverManager.setLoginTimeout(seconds);
    }

    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    public MySplitterRootConfig getMySplitterConfig() {
        return mySplitterConfig;
    }

    public Map<String, Object> getStatus() {
        return dataSourceManager.getStatus();
    }

}
