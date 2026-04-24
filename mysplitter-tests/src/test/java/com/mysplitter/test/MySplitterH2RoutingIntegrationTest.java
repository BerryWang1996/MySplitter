package com.mysplitter.test;

import com.mysplitter.MySplitterDataSource;
import com.mysplitter.config.MySplitterCommonConfig;
import com.mysplitter.config.MySplitterConfig;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.After;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MySplitterH2RoutingIntegrationTest {

    private static final String H2_DRIVER_CLASS_NAME = "org.h2.Driver";

    @After
    public void resetFilters() {
        TestRouteRecordingFilter.reset();
    }

    @Test
    public void shouldRouteReadQueriesToReaderWhenAutoCommitEnabled() throws Exception {
        MySplitterDataSource dataSource = createDataSource();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            long firstConnectionId = querySessionId(connection);
            long secondConnectionId = querySessionId(connection);

            assertEquals(firstConnectionId, secondConnectionId);
            assertRouteCount("reader-node", 2);
        } finally {
            closeQuietly(connection);
            dataSource.close();
        }
    }

    @Test
    public void shouldRouteWriteQueriesToWriterWhenAutoCommitEnabled() throws Exception {
        MySplitterDataSource dataSource = createDataSource();
        Connection connection = null;
        Statement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            statement.execute("CREATE TABLE IF NOT EXISTS routing_probe(id BIGINT PRIMARY KEY)");

            assertRouteCount("writer-node", 1);
        } finally {
            closeQuietly(statement);
            closeQuietly(connection);
            dataSource.close();
        }
    }

    @Test
    public void shouldPinTransactionalReadsToWriterConnection() throws Exception {
        MySplitterDataSource dataSource = createDataSource();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            long firstConnectionId = querySessionId(connection);
            long secondConnectionId = querySessionId(connection);

            assertEquals(firstConnectionId, secondConnectionId);
            assertRouteCount("writer-node", 2);
        } finally {
            rollbackQuietly(connection);
            closeQuietly(connection);
            dataSource.close();
        }
    }

    private MySplitterDataSource createDataSource() {
        MySplitterDataSource dataSource = new MySplitterDataSource(createRootConfig());
        dataSource.init();
        return dataSource;
    }

    private MySplitterRootConfig createRootConfig() {
        String databaseSuffix = UUID.randomUUID().toString().replace("-", "");
        MySplitterRootConfig rootConfig = new MySplitterRootConfig();
        MySplitterConfig mySplitterConfig = new MySplitterConfig();
        rootConfig.setMysplitter(mySplitterConfig);

        mySplitterConfig.setReadAndWriteParser("com.mysplitter.DefaultReadAndWriteParser");
        mySplitterConfig.setFilters(Arrays.asList(TestRouteRecordingFilter.class.getName()));

        MySplitterCommonConfig commonConfig = new MySplitterCommonConfig();
        commonConfig.setDataSourceClass("com.zaxxer.hikari.HikariDataSource");
        mySplitterConfig.setCommon(commonConfig);

        LinkedHashMap<String, MySplitterDataBaseConfig> databases = new LinkedHashMap<String, MySplitterDataBaseConfig>();
        MySplitterDataBaseConfig databaseConfig = new MySplitterDataBaseConfig();
        databaseConfig.setReaders(createNodes("reader-node", "reader_" + databaseSuffix));
        databaseConfig.setWriters(createNodes("writer-node", "writer_" + databaseSuffix));
        databases.put("database-main", databaseConfig);
        mySplitterConfig.setDatabases(databases);

        return rootConfig;
    }

    private LinkedHashMap<String, MySplitterDataSourceNodeConfig> createNodes(String nodeName, String databaseName) {
        LinkedHashMap<String, MySplitterDataSourceNodeConfig> nodes =
                new LinkedHashMap<String, MySplitterDataSourceNodeConfig>();
        MySplitterDataSourceNodeConfig nodeConfig = new MySplitterDataSourceNodeConfig();
        nodeConfig.setWeight(1);
        nodeConfig.setConfiguration(createDataSourceConfiguration(databaseName));
        nodes.put(nodeName, nodeConfig);
        return nodes;
    }

    private Map<String, Object> createDataSourceConfiguration(String databaseName) {
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("jdbcUrl", "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        configuration.put("username", "sa");
        configuration.put("password", "");
        configuration.put("driverClassName", H2_DRIVER_CLASS_NAME);
        configuration.put("connectionTimeout", Long.valueOf(1000L));
        configuration.put("maximumPoolSize", Integer.valueOf(1));
        configuration.put("minimumIdle", Integer.valueOf(0));
        return configuration;
    }

    private long querySessionId(Connection connection) throws Exception {
        Exception exceptionHolder = null;
        String[] candidates = new String[]{"SELECT SESSION_ID()", "CALL SESSION_ID()"};
        for (String candidate : candidates) {
            try {
                return queryLong(connection, candidate);
            } catch (Exception e) {
                exceptionHolder = e;
            }
        }
        throw exceptionHolder;
    }

    private long queryLong(Connection connection, String sql) throws Exception {
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        } finally {
            closeQuietly(resultSet);
            closeQuietly(preparedStatement);
        }
    }

    private void assertRouteCount(String expectedNodeName, int expectedCount) {
        List<String> routes = TestRouteRecordingFilter.snapshot();
        assertEquals(expectedCount, routes.size());
        for (String route : routes) {
            assertEquals(expectedNodeName, parseNodeName(route));
        }
    }

    private String parseNodeName(String routeRecord) {
        String[] parts = routeRecord.split("\\|");
        return parts[1];
    }

    private void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (Exception e) {
            // Ignore cleanup failures in tests.
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            // Ignore cleanup failures in tests.
        }
    }
}
