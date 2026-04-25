package com.mysplitter.reflect;

import com.mysplitter.MySplitterDataSource;
import com.mysplitter.advise.DatabasesRoutingHandlerAdvise;
import com.mysplitter.config.MySplitterCommonConfig;
import com.mysplitter.config.MySplitterConfig;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertArrayEquals;

public class MySplitterStatementBatchReflectionTest {

    @Test
    public void shouldExecuteBatchesForAllRoutesInOriginalAddOrder() throws Exception {
        MySplitterDataSource dataSource = createDataSource();
        Connection connection = null;
        Statement statement = null;
        try {
            dataSource.init();
            connection = dataSource.getConnection();
            statement = connection.createStatement();

            statement.addBatch("[database-a] UPDATE account SET balance = balance + 1");
            statement.addBatch("[database-b] UPDATE account SET balance = balance - 1");
            statement.addBatch("[database-a] UPDATE account SET balance = balance + 2");

            assertArrayEquals(new int[]{11, 21, 12}, statement.executeBatch());
        } finally {
            closeQuietly(statement);
            closeQuietly(connection);
            dataSource.close();
        }
    }

    @Test
    public void shouldClearBatchesAcrossAllRoutes() throws Exception {
        MySplitterDataSource dataSource = createDataSource();
        Connection connection = null;
        Statement statement = null;
        try {
            dataSource.init();
            connection = dataSource.getConnection();
            statement = connection.createStatement();

            statement.addBatch("[database-a] UPDATE account SET balance = balance + 1");
            statement.addBatch("[database-b] UPDATE account SET balance = balance - 1");
            statement.clearBatch();
            statement.addBatch("[database-b] UPDATE account SET balance = balance - 2");

            assertArrayEquals(new int[]{21}, statement.executeBatch());
        } finally {
            closeQuietly(statement);
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

        private static final Map<String, Integer> BASE_COUNTS = new HashMap<String, Integer>();

        static {
            BASE_COUNTS.put("writer-a", Integer.valueOf(10));
            BASE_COUNTS.put("writer-b", Integer.valueOf(20));
        }

        private String nodeName;

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return createConnectionProxy(nodeName);
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

        private Connection createConnectionProxy(final String currentNodeName) {
            final Connection[] connectionHolder = new Connection[1];
            connectionHolder[0] = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    new InvocationHandler() {
                        private boolean closed;

                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String methodName = method.getName();
                            if ("createStatement".equals(methodName)) {
                                return createStatementProxy(currentNodeName, connectionHolder[0]);
                            }
                            if ("close".equals(methodName)) {
                                closed = true;
                                return null;
                            }
                            if ("isClosed".equals(methodName)) {
                                return Boolean.valueOf(closed);
                            }
                            if ("unwrap".equals(methodName)) {
                                throw new SQLException("Not a wrapper.");
                            }
                            if ("isWrapperFor".equals(methodName)) {
                                return Boolean.FALSE;
                            }
                            return defaultValue(method.getReturnType());
                        }
                    });
            return connectionHolder[0];
        }

        private Statement createStatementProxy(final String currentNodeName, final Connection connection) {
            return (Statement) Proxy.newProxyInstance(
                    Statement.class.getClassLoader(),
                    new Class<?>[]{Statement.class},
                    new InvocationHandler() {
                        private final List<String> batchSql = new ArrayList<String>();
                        private boolean closed;

                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String methodName = method.getName();
                            if ("addBatch".equals(methodName)) {
                                batchSql.add((String) args[0]);
                                return null;
                            }
                            if ("clearBatch".equals(methodName)) {
                                batchSql.clear();
                                return null;
                            }
                            if ("executeBatch".equals(methodName)) {
                                int[] result = new int[batchSql.size()];
                                int base = BASE_COUNTS.get(currentNodeName).intValue();
                                for (int i = 0; i < batchSql.size(); i++) {
                                    result[i] = base + i + 1;
                                }
                                batchSql.clear();
                                return result;
                            }
                            if ("getConnection".equals(methodName)) {
                                return connection;
                            }
                            if ("close".equals(methodName)) {
                                closed = true;
                                return null;
                            }
                            if ("isClosed".equals(methodName)) {
                                return Boolean.valueOf(closed);
                            }
                            if ("unwrap".equals(methodName)) {
                                throw new SQLException("Not a wrapper.");
                            }
                            if ("isWrapperFor".equals(methodName)) {
                                return Boolean.FALSE;
                            }
                            return defaultValue(method.getReturnType());
                        }
                    });
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
