package com.mysplitter;

import com.mysplitter.config.*;
import com.mysplitter.util.ConfigurationUtil;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class MySplitterDataSourceRouter implements DataSource, Serializable {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDataSourceRouter.class);

    private static AtomicBoolean isInitialized = new AtomicBoolean(false);

    public void init() throws Exception {
        if (isInitialized.compareAndSet(false, true)) {
            // 获取配置文件
            LOGGER.info("MySplitter is initializing.");
            MySplitterRootConfig mySplitterConfig = ConfigurationUtil.getMySplitterConfig();
            // 对配置文件进行检查
            ConfigurationUtil.checkMySplitterConfig(mySplitterConfig);
            LOGGER.info("MySplitter configuration passed.");
            // 遍历所有数据库的数据源
            Map<String, MySplitterDataBaseConfig> databases = mySplitterConfig.getMysplitter().getDatabases();
            for (String databaseKey : databases.keySet()) {
                MySplitterDataBaseConfig mySplitterDataBaseConfig = databases.get(databaseKey);
                Map<String, MySplitterIntegrateConfig> integrates = mySplitterDataBaseConfig.getIntegrates();
                if (integrates != null && integrates.size() > 0) {
                    for (String integrateKey : integrates.keySet()) {
                        createIntegrateDataSource(mySplitterConfig.getMysplitter().getCommon(),
                                mySplitterDataBaseConfig,
                                integrates.get(integrateKey));
                    }
                }
                Map<String, MySplitterReaderConfig> readers = mySplitterDataBaseConfig.getReaders();
                if (readers != null && readers.size() > 0) {
                    for (String readerKey : readers.keySet()) {
                        createReaderDataSource(mySplitterConfig.getMysplitter().getCommon(),
                                mySplitterDataBaseConfig,
                                readers.get(readerKey));
                    }
                }
                Map<String, MySplitterWriterConfig> writers = mySplitterDataBaseConfig.getWriters();
                if (writers != null && writers.size() > 0) {
                    for (String writerKey : writers.keySet()) {
                        createWriterDataSource(mySplitterConfig.getMysplitter().getCommon(),
                                mySplitterDataBaseConfig,
                                writers.get(writerKey));
                    }
                }
            }
            LOGGER.info("MySplitter has been initialized successful.");
        }
    }

    private void createWriterDataSource(MySplitterCommonConfig common,
                                        MySplitterDataBaseConfig mySplitterDataBaseConfig,
                                        MySplitterWriterConfig mySplitterWriterConfig) {
        // 根据高可用-是否懒加载创建数据源
        // 根据高可用-切换时机判断是否定时切换
        // 根据高可用-心跳语句和速度创建定时检查任务
        // 根据高可用-连接死亡警告处理器创建死亡警告处理任务
        // 根据负载均衡-轮询（根据weight设置获取连接算法）
        // 根据多数据库路由设置获取连接算法
        // 创建销毁连接守护线程
    }

    private void createReaderDataSource(MySplitterCommonConfig common,
                                        MySplitterDataBaseConfig mySplitterDataBaseConfig,
                                        MySplitterReaderConfig mySplitterReaderConfig) {

    }

    private void createIntegrateDataSource(MySplitterCommonConfig common,
                                           MySplitterDataBaseConfig mySplitterDataBaseConfig,
                                           MySplitterIntegrateConfig mySplitterIntegrateConfig) {

    }

    public Connection getConnection() throws SQLException {
        return null;
    }

    public Connection getConnection(String username, String password) throws SQLException {
        throw new UnsupportedOperationException("Not supported by MySplitter");
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public PrintWriter getLogWriter() throws SQLException {
        // TODO 未填写
        return null;
    }

    public void setLogWriter(PrintWriter out) throws SQLException {
        // TODO 未填写
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

}
