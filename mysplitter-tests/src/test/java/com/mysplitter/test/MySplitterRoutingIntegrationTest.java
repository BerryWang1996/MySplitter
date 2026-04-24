package com.mysplitter.test;

import com.mysplitter.MySplitterDataSource;
import com.mysplitter.config.MySplitterCommonConfig;
import com.mysplitter.config.MySplitterConfig;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MySplitterRoutingIntegrationTest {

    private static MySQLContainer MYSQL_CONTAINER;

    @BeforeClass
    public static void setUpContainer() {
        Assume.assumeTrue(isDockerAvailable());
        MYSQL_CONTAINER = new MySQLContainer("mysql:5.7.34")
                .withDatabaseName("mysplitter_it")
                .withUsername("test")
                .withPassword("test");
        MYSQL_CONTAINER.start();
    }

    @AfterClass
    public static void tearDownContainer() {
        if (MYSQL_CONTAINER != null) {
            MYSQL_CONTAINER.stop();
        }
    }

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
            long firstConnectionId = queryConnectionId(connection);
            long secondConnectionId = queryConnectionId(connection);

            assertEquals(firstConnectionId, secondConnectionId);
            assertRouteCount("reader-node", 2);
        } finally {
            if (connection != null) {
                connection.close();
            }
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

            assertSingleRoute("writer-node");
        } finally {
            if (statement != null) {
                statement.close();
            }
            if (connection != null) {
                connection.close();
            }
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

            long firstConnectionId = queryConnectionId(connection);
            long secondConnectionId = queryConnectionId(connection);

            assertEquals(firstConnectionId, secondConnectionId);
            assertRouteCount("writer-node", 2);
        } finally {
            if (connection != null) {
                connection.rollback();
                connection.close();
            }
            dataSource.close();
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable throwable) {
            return false;
        }
    }

    private MySplitterDataSource createDataSource() {
        MySplitterDataSource dataSource = new MySplitterDataSource(createRootConfig());
        dataSource.init();
        return dataSource;
    }

    private MySplitterRootConfig createRootConfig() {
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
        databaseConfig.setReaders(createNodes("reader-node"));
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
        configuration.put("jdbcUrl", MYSQL_CONTAINER.getJdbcUrl());
        configuration.put("username", MYSQL_CONTAINER.getUsername());
        configuration.put("password", MYSQL_CONTAINER.getPassword());
        configuration.put("driverClassName", MYSQL_CONTAINER.getDriverClassName());
        configuration.put("connectionTimeout", Long.valueOf(1000L));
        return configuration;
    }

    private long queryConnectionId(Connection connection) throws Exception {
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            preparedStatement = connection.prepareStatement("SELECT CONNECTION_ID()");
            resultSet = preparedStatement.executeQuery();
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
            if (preparedStatement != null) {
                preparedStatement.close();
            }
        }
    }

    private void assertSingleRoute(String expectedNodeName) {
        assertRouteCount(expectedNodeName, 1);
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
}
