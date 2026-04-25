package com.mysplitter.reflect;

import com.mysplitter.MySplitterDataSource;
import com.mysplitter.advise.DatabasesRoutingHandlerAdvise;
import com.mysplitter.config.MySplitterCommonConfig;
import com.mysplitter.config.MySplitterConfig;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.After;
import org.junit.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MySplitterTransactionGuardrailReflectionTest {

    @After
    public void resetDataSourceState() {
        ControlledDataSource.reset();
    }

    @Test
    public void shouldRejectTransactionThatTouchesSecondRoute() throws Exception {
        MySplitterDataSource dataSource = createDataSource();
        Connection connection = null;
        try {
            dataSource.init();
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            connection.prepareStatement("[database-a] UPDATE account SET balance = balance + 1");

            try {
                connection.prepareStatement("[database-b] UPDATE account SET balance = balance - 1");
                fail("Expected a local transaction spanning multiple physical connections to be rejected.");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("multiple physical connections"));
            }
            assertEquals(1, ControlledDataSource.getAttempts("writer-a"));
            assertEquals(0, ControlledDataSource.getAttempts("writer-b"));
        } finally {
            closeQuietly(connection);
            dataSource.close();
        }
    }

    @Test
    public void shouldAllowTransactionToReuseTheSameRoute() throws Exception {
        MySplitterDataSource dataSource = createDataSource();
        Connection connection = null;
        try {
            dataSource.init();
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            connection.prepareStatement("[database-a] UPDATE account SET balance = balance + 1");
            connection.prepareStatement("[database-a] UPDATE account SET balance = balance + 2");

            assertEquals(1, ControlledDataSource.getAttempts("writer-a"));
        } finally {
            closeQuietly(connection);
            dataSource.close();
        }
    }

    @Test
    public void shouldRejectAdministrativeConnectionInsideExistingTransactionRoute() throws Exception {
        MySplitterDataSource dataSource = createDataSource();
        Connection connection = null;
        try {
            dataSource.init();
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            connection.prepareStatement("[database-a] UPDATE account SET balance = balance + 1");

            try {
                connection.getMetaData();
                fail("Expected administrative connection to be rejected inside an existing routed transaction.");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("multiple physical connections"));
            }
        } finally {
            closeQuietly(connection);
            dataSource.close();
        }
    }

    private MySplitterDataSource createDataSource() {
        return new MySplitterDataSource(createRootConfig());
    }

    private MySplitterRootConfig createRootConfig() {
        MySplitterRootConfig rootConfig = new MySplitterRootConfig();
        MySplitterConfig mySplitterConfig = new MySplitterConfig();
        rootConfig.setMysplitter(mySplitterConfig);

        mySplitterConfig.setDatabasesRoutingHandler(PrefixRoutingHandler.class.getName());
        MySplitterCommonConfig commonConfig = new MySplitterCommonConfig();
        commonConfig.setDataSourceClass(ControlledDataSource.class.getName());
        mySplitterConfig.setCommon(commonConfig);

        LinkedHashMap<String, MySplitterDataBaseConfig> databases =
                new LinkedHashMap<String, MySplitterDataBaseConfig>();
        databases.put("database-a", createDatabaseConfig("writer-a"));
        databases.put("database-b", createDatabaseConfig("writer-b"));
        mySplitterConfig.setDatabases(databases);
        return rootConfig;
    }

    private MySplitterDataBaseConfig createDatabaseConfig(String writerNodeName) {
        MySplitterDataBaseConfig databaseConfig = new MySplitterDataBaseConfig();
        LinkedHashMap<String, MySplitterDataSourceNodeConfig> writers =
                new LinkedHashMap<String, MySplitterDataSourceNodeConfig>();
        writers.put(writerNodeName, createNodeConfig(writerNodeName));
        databaseConfig.setWriters(writers);
        return databaseConfig;
    }

    private MySplitterDataSourceNodeConfig createNodeConfig(String nodeName) {
        MySplitterDataSourceNodeConfig nodeConfig = new MySplitterDataSourceNodeConfig();
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("nodeName", nodeName);
        nodeConfig.setConfiguration(configuration);
        return nodeConfig;
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception e) {
            // Ignore cleanup failures in tests.
        }
    }

    public static final class PrefixRoutingHandler implements DatabasesRoutingHandlerAdvise {

        @Override
        public String routerHandler(String sql) {
            if (sql.startsWith("[database-b]")) {
                return "database-b";
            }
            return "database-a";
        }

        @Override
        public String rewriteSql(String sql) {
            if (sql.startsWith("[database-a]")) {
                return sql.substring("[database-a]".length()).trim();
            }
            if (sql.startsWith("[database-b]")) {
                return sql.substring("[database-b]".length()).trim();
            }
            return sql;
        }
    }

    public static final class ControlledDataSource implements DataSource {

        private static final ConcurrentHashMap<String, AtomicInteger> ATTEMPTS =
                new ConcurrentHashMap<String, AtomicInteger>();

        private String nodeName;

        public static void reset() {
            ATTEMPTS.clear();
        }

        public static int getAttempts(String nodeName) {
            AtomicInteger attempts = ATTEMPTS.get(nodeName);
            return attempts == null ? 0 : attempts.get();
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        @Override
        public Connection getConnection() throws SQLException {
            AtomicInteger attemptCounter = ATTEMPTS.get(nodeName);
            if (attemptCounter == null) {
                AtomicInteger newCounter = new AtomicInteger(0);
                AtomicInteger existingCounter = ATTEMPTS.putIfAbsent(nodeName, newCounter);
                attemptCounter = existingCounter == null ? newCounter : existingCounter;
            }
            attemptCounter.incrementAndGet();
            return createConnectionProxy();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper.");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        private Connection createConnectionProxy() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    new DefaultInvocationHandler());
        }
    }

    private static final class DefaultInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("prepareStatement".equals(methodName)) {
                return Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[]{PreparedStatement.class},
                        new DefaultInvocationHandler());
            }
            if ("getMetaData".equals(methodName)) {
                return Proxy.newProxyInstance(
                        DatabaseMetaData.class.getClassLoader(),
                        new Class<?>[]{DatabaseMetaData.class},
                        new DefaultInvocationHandler());
            }
            if ("unwrap".equals(methodName)) {
                throw new SQLException("Not a wrapper.");
            }
            if ("isWrapperFor".equals(methodName)) {
                return Boolean.FALSE;
            }
            return defaultValue(method.getReturnType());
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == null || Void.TYPE.equals(returnType)) {
                return null;
            }
            if (Boolean.TYPE.equals(returnType)) {
                return Boolean.FALSE;
            }
            if (Integer.TYPE.equals(returnType)) {
                return Integer.valueOf(0);
            }
            if (Long.TYPE.equals(returnType)) {
                return Long.valueOf(0L);
            }
            if (Double.TYPE.equals(returnType)) {
                return Double.valueOf(0D);
            }
            if (Float.TYPE.equals(returnType)) {
                return Float.valueOf(0F);
            }
            if (Short.TYPE.equals(returnType)) {
                return Short.valueOf((short) 0);
            }
            if (Byte.TYPE.equals(returnType)) {
                return Byte.valueOf((byte) 0);
            }
            if (Character.TYPE.equals(returnType)) {
                return Character.valueOf((char) 0);
            }
            return null;
        }
    }
}
