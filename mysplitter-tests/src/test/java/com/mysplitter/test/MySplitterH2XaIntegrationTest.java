package com.mysplitter.test;

import com.mysplitter.MySplitterDataSource;
import com.mysplitter.advise.DatabasesRoutingHandlerAdvise;
import com.mysplitter.config.MySplitterCommonConfig;
import com.mysplitter.config.MySplitterConfig;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterRootConfig;
import com.mysplitter.config.MySplitterTransactionConfig;
import com.mysplitter.config.MySplitterTransactionCoordinatorConfig;
import com.mysplitter.exceptions.MySplitterInitException;
import org.junit.After;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MySplitterH2XaIntegrationTest {

    private static final String EXPERIMENTAL_XA_PROPERTY = "mysplitter.experimental.xa.enabled";

    private static final String H2_DRIVER_CLASS_NAME = "org.h2.Driver";

    private static final String H2_XA_DATA_SOURCE_CLASS_NAME = "org.h2.jdbcx.JdbcDataSource";

    @After
    public void cleanup() {
        System.clearProperty(EXPERIMENTAL_XA_PROPERTY);
        TestRouteRecordingFilter.reset();
    }

    @Test
    public void shouldCommitTwoRoutedH2XaBranchesAtomically() throws Exception {
        System.setProperty(EXPERIMENTAL_XA_PROPERTY, "true");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String databaseAUrl = h2Url("xa_a_" + suffix);
        String databaseBUrl = h2Url("xa_b_" + suffix);
        prepareTable(databaseAUrl, "tx_probe_a");
        prepareTable(databaseBUrl, "tx_probe_b");

        MySplitterDataSource dataSource = new MySplitterDataSource(createRootConfig(databaseAUrl, databaseBUrl));
        Connection connection = null;
        Statement statement = null;
        try {
            dataSource.init();
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            statement = connection.createStatement();

            statement.executeUpdate("/*db:a*/ INSERT INTO tx_probe_a(id, name) VALUES(1, 'alpha')");
            statement.executeUpdate("/*db:b*/ INSERT INTO tx_probe_b(id, name) VALUES(2, 'beta')");
            connection.commit();

            assertEquals("alpha", queryName(databaseAUrl, "tx_probe_a", 1));
            assertEquals("beta", queryName(databaseBUrl, "tx_probe_b", 2));
            assertRouteSeen("database-a", "writer-a");
            assertRouteSeen("database-b", "writer-b");
        } finally {
            closeQuietly(statement);
            closeQuietly(connection);
            dataSource.close();
        }
    }

    @Test
    public void shouldRollbackTwoRoutedH2XaBranchesTogether() throws Exception {
        System.setProperty(EXPERIMENTAL_XA_PROPERTY, "true");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String databaseAUrl = h2Url("xa_rollback_a_" + suffix);
        String databaseBUrl = h2Url("xa_rollback_b_" + suffix);
        prepareTable(databaseAUrl, "tx_probe_a");
        prepareTable(databaseBUrl, "tx_probe_b");

        MySplitterDataSource dataSource = new MySplitterDataSource(createRootConfig(databaseAUrl, databaseBUrl));
        Connection connection = null;
        Statement statement = null;
        try {
            dataSource.init();
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            statement = connection.createStatement();

            statement.executeUpdate("/*db:a*/ INSERT INTO tx_probe_a(id, name) VALUES(1, 'alpha')");
            statement.executeUpdate("/*db:b*/ INSERT INTO tx_probe_b(id, name) VALUES(2, 'beta')");
            connection.rollback();

            assertEquals(0, queryRowCount(databaseAUrl, "tx_probe_a"));
            assertEquals(0, queryRowCount(databaseBUrl, "tx_probe_b"));
            assertRouteSeen("database-a", "writer-a");
            assertRouteSeen("database-b", "writer-b");
        } finally {
            closeQuietly(statement);
            closeQuietly(connection);
            dataSource.close();
        }
    }

    @Test
    public void shouldRejectNonXaDatasourceBeforeTransactionWorkStarts() {
        System.setProperty(EXPERIMENTAL_XA_PROPERTY, "true");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String databaseAUrl = h2Url("xa_required_a_" + suffix);
        String databaseBUrl = h2Url("xa_required_b_" + suffix);
        MySplitterDataSource dataSource = new MySplitterDataSource(
                createRootConfig(databaseAUrl, databaseBUrl, true, false));

        try {
            dataSource.init();
            fail("Expected non-XA datasource to be rejected during initialization.");
        } catch (MySplitterInitException e) {
            assertTrue(hasMessage(e, "writer-b"));
            assertTrue(hasMessage(e, "not XA capable"));
        } finally {
            dataSource.close();
        }
    }

    private MySplitterRootConfig createRootConfig(String databaseAUrl, String databaseBUrl) {
        return createRootConfig(databaseAUrl, databaseBUrl, true, true);
    }

    private MySplitterRootConfig createRootConfig(String databaseAUrl,
                                                  String databaseBUrl,
                                                  boolean databaseAXaCapable,
                                                  boolean databaseBXaCapable) {
        MySplitterRootConfig rootConfig = new MySplitterRootConfig();
        MySplitterConfig mySplitterConfig = new MySplitterConfig();
        rootConfig.setMysplitter(mySplitterConfig);

        mySplitterConfig.setDatabasesRoutingHandler(TestDatabaseRoutingHandler.class.getName());
        mySplitterConfig.setReadAndWriteParser("com.mysplitter.DefaultReadAndWriteParser");
        mySplitterConfig.setFilters(Arrays.asList(TestRouteRecordingFilter.class.getName()));
        mySplitterConfig.setCommon(commonConfig());
        mySplitterConfig.setTransaction(xaTransactionConfig());

        LinkedHashMap<String, MySplitterDataBaseConfig> databases =
                new LinkedHashMap<String, MySplitterDataBaseConfig>();
        databases.put("database-a", databaseConfig("writer-a", databaseAUrl, databaseAXaCapable));
        databases.put("database-b", databaseConfig("writer-b", databaseBUrl, databaseBXaCapable));
        mySplitterConfig.setDatabases(databases);
        return rootConfig;
    }

    private MySplitterCommonConfig commonConfig() {
        MySplitterCommonConfig commonConfig = new MySplitterCommonConfig();
        commonConfig.setDataSourceClass("com.zaxxer.hikari.HikariDataSource");
        return commonConfig;
    }

    private MySplitterTransactionConfig xaTransactionConfig() {
        MySplitterTransactionConfig transactionConfig = new MySplitterTransactionConfig();
        transactionConfig.setMode(MySplitterTransactionConfig.MODE_XA);
        MySplitterTransactionCoordinatorConfig coordinatorConfig = new MySplitterTransactionCoordinatorConfig();
        coordinatorConfig.setType("embedded");
        coordinatorConfig.setLogStore("memory");
        transactionConfig.setCoordinator(coordinatorConfig);
        return transactionConfig;
    }

    private MySplitterDataBaseConfig databaseConfig(String writerNodeName, String jdbcUrl, boolean xaCapable) {
        MySplitterDataBaseConfig databaseConfig = new MySplitterDataBaseConfig();
        LinkedHashMap<String, MySplitterDataSourceNodeConfig> writers =
                new LinkedHashMap<String, MySplitterDataSourceNodeConfig>();
        MySplitterDataSourceNodeConfig nodeConfig = new MySplitterDataSourceNodeConfig();
        nodeConfig.setWeight(1);
        nodeConfig.setConfiguration(dataSourceConfiguration(jdbcUrl, xaCapable));
        writers.put(writerNodeName, nodeConfig);
        databaseConfig.setWriters(writers);
        return databaseConfig;
    }

    private Map<String, Object> dataSourceConfiguration(String jdbcUrl, boolean xaCapable) {
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("jdbcUrl", jdbcUrl);
        configuration.put("username", "sa");
        configuration.put("password", "");
        configuration.put("driverClassName", H2_DRIVER_CLASS_NAME);
        configuration.put("connectionTimeout", Long.valueOf(1000L));
        configuration.put("maximumPoolSize", Integer.valueOf(1));
        configuration.put("minimumIdle", Integer.valueOf(0));
        if (!xaCapable) {
            return configuration;
        }
        configuration.put("xaDataSourceClass", H2_XA_DATA_SOURCE_CLASS_NAME);

        Map<String, Object> xaProperties = new HashMap<String, Object>();
        xaProperties.put("URL", jdbcUrl);
        xaProperties.put("url", jdbcUrl);
        xaProperties.put("user", "sa");
        xaProperties.put("password", "");
        configuration.put("xaProperties", xaProperties);
        return configuration;
    }

    private String h2Url(String databaseName) {
        return "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    private void prepareTable(String jdbcUrl, String tableName) throws Exception {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = DriverManager.getConnection(jdbcUrl, "sa", "");
            statement = connection.createStatement();
            statement.execute("CREATE TABLE " + tableName + "(id INT PRIMARY KEY, name VARCHAR(32))");
        } finally {
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    private boolean hasMessage(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String queryName(String jdbcUrl, String tableName, int id) throws Exception {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = DriverManager.getConnection(jdbcUrl, "sa", "");
            statement = connection.createStatement();
            resultSet = statement.executeQuery("SELECT name FROM " + tableName + " WHERE id = " + id);
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        } finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    private int queryRowCount(String jdbcUrl, String tableName) throws Exception {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = DriverManager.getConnection(jdbcUrl, "sa", "");
            statement = connection.createStatement();
            resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName);
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        } finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    private void assertRouteSeen(String databaseName, String nodeName) {
        for (String route : TestRouteRecordingFilter.snapshot()) {
            String[] parts = route.split("\\|");
            if (databaseName.equals(parts[0]) && nodeName.equals(parts[1])) {
                return;
            }
        }
        throw new AssertionError("Expected route " + databaseName + "|" + nodeName + " was not recorded.");
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

    public static final class TestDatabaseRoutingHandler implements DatabasesRoutingHandlerAdvise {

        @Override
        public String routerHandler(String sql) {
            if (sql != null && sql.contains("/*db:b*/")) {
                return "database-b";
            }
            return "database-a";
        }

        @Override
        public String rewriteSql(String sql) {
            if (sql == null) {
                return null;
            }
            return sql.replace("/*db:a*/", "").replace("/*db:b*/", "");
        }
    }
}
