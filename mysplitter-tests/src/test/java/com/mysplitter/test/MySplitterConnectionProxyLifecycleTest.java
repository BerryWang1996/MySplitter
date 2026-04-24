package com.mysplitter.test;

import com.mysplitter.MySplitterDataSource;
import com.mysplitter.config.MySplitterCommonConfig;
import com.mysplitter.config.MySplitterConfig;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MySplitterConnectionProxyLifecycleTest {

    @Test
    public void shouldCloseLogicalConnectionWhenAbortHappensBeforeFirstPhysicalConnection() throws Exception {
        MySplitterDataSource dataSource = new MySplitterDataSource(createRootConfig());
        Connection connection = null;
        try {
            dataSource.init();
            connection = dataSource.getConnection();

            connection.abort(new Executor() {
                @Override
                public void execute(Runnable command) {
                }
            });

            assertTrue(connection.isClosed());
            assertConnectionClosed(connection);
        } finally {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            dataSource.close();
        }
    }

    private void assertConnectionClosed(Connection connection) throws SQLException {
        try {
            connection.createStatement();
            fail("Connection should be closed after abort.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("Connection is closed"));
        }
    }

    private MySplitterRootConfig createRootConfig() {
        MySplitterRootConfig rootConfig = new MySplitterRootConfig();
        MySplitterConfig mySplitterConfig = new MySplitterConfig();
        rootConfig.setMysplitter(mySplitterConfig);

        MySplitterCommonConfig commonConfig = new MySplitterCommonConfig();
        commonConfig.setDataSourceClass("com.alibaba.druid.pool.DruidDataSource");
        mySplitterConfig.setCommon(commonConfig);

        LinkedHashMap<String, MySplitterDataBaseConfig> databases =
                new LinkedHashMap<String, MySplitterDataBaseConfig>();
        MySplitterDataBaseConfig databaseConfig = new MySplitterDataBaseConfig();
        databaseConfig.setWriters(createNodes("writer-node"));
        databases.put("database-main", databaseConfig);
        mySplitterConfig.setDatabases(databases);

        return rootConfig;
    }

    private LinkedHashMap<String, MySplitterDataSourceNodeConfig> createNodes(String nodeName) {
        LinkedHashMap<String, MySplitterDataSourceNodeConfig> nodes =
                new LinkedHashMap<String, MySplitterDataSourceNodeConfig>();
        MySplitterDataSourceNodeConfig nodeConfig = new MySplitterDataSourceNodeConfig();
        nodeConfig.setWeight(1);
        nodeConfig.setConfiguration(createDataSourceConfiguration());
        nodes.put(nodeName, nodeConfig);
        return nodes;
    }

    private Map<String, Object> createDataSourceConfiguration() {
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("url", "jdbc:mysql://127.0.0.1:65535/mysplitter_abort");
        configuration.put("username", "test");
        configuration.put("password", "test");
        configuration.put("driverClassName", "com.mysql.jdbc.Driver");
        return configuration;
    }
}
