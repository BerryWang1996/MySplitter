package com.mysplitter;

import com.mysplitter.config.MySplitterRootConfig;
import com.mysplitter.exceptions.MySplitterInitException;
import com.mysplitter.util.ConfigurationUtil;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * MySplitter数据源路由，实现DataSource接口
 *
 * @Author: wangbor
 * @Date: 2018/5/14 10:30
 */
public class MySplitterDataSource implements DataSource, Serializable {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDataSource.class);

    private static final String DEFAULT_CONFIGURATION_FILE_NAME = "mysplitter.yml";

    private MySplitterDatasourceManager datasourceManager = new MySplitterDatasourceManager(this);

    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    private MySplitterRootConfig mySplitterConfig;

    public MySplitterDataSource() {
    }

    public MySplitterDataSource(MySplitterRootConfig mySplitterConfig) {
        this.mySplitterConfig = mySplitterConfig;
    }

    public void init() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                // 获取配置文件
                LOGGER.info("MySplitter is initializing.");
                mySplitterConfig = ConfigurationUtil.getMySplitterConfig(DEFAULT_CONFIGURATION_FILE_NAME);
                // 对配置文件进行检查
                ConfigurationUtil.checkMySplitterConfig(mySplitterConfig);
                LOGGER.info("MySplitter configuration passed.");
                // 初始化数据源管理器
                datasourceManager.init();
                LOGGER.info("MySplitter has been initialized successful.");
            } catch (Exception e) {
                new MySplitterInitException(e).printStackTrace();
                System.exit(1);
            }
        }
    }

    public Connection getConnection() throws SQLException {
        init();
        return null;
    }

    public Connection getConnection(String username, String password) throws SQLException {
        init();
        return null;
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
}