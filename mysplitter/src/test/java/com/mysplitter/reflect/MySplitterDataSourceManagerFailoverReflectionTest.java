package com.mysplitter.reflect;

import org.junit.After;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MySplitterDataSourceManagerFailoverReflectionTest {

    @After
    public void resetDataSourceState() {
        ControlledDataSource.reset();
    }

    @Test
    public void shouldTryEachRouteNodeOnlyOnceWhenAllHealthyNodesFail() throws Exception {
        Object dataSource = createDataSource("setReaders", "reader-a", "reader-b");
        Class<?> dataSourceClass = dataSource.getClass();
        Connection connection = null;
        try {
            invoke(dataSourceClass, dataSource, "init", new Class<?>[0], new Object[0]);
            connection = (Connection) invoke(dataSourceClass, dataSource, "getConnection", new Class<?>[0], new Object[0]);
            try {
                connection.prepareStatement("SELECT 1");
                fail("Route selection should fail when all reader nodes reject the connection.");
            } catch (SQLException expected) {
                assertEquals(1, ControlledDataSource.getAttempts("reader-a"));
                assertEquals(1, ControlledDataSource.getAttempts("reader-b"));
            }
        } finally {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            invoke(dataSourceClass, dataSource, "close", new Class<?>[0], new Object[0]);
        }
    }

    @Test
    public void shouldTryEachAdministrativeNodeOnlyOnceWhenDefaultConnectionFails() throws Exception {
        Object dataSource = createDataSource("setWriters", "writer-a", "writer-b");
        Class<?> dataSourceClass = dataSource.getClass();
        Connection connection = null;
        try {
            invoke(dataSourceClass, dataSource, "init", new Class<?>[0], new Object[0]);
            connection = (Connection) invoke(dataSourceClass, dataSource, "getConnection", new Class<?>[0], new Object[0]);
            try {
                connection.getMetaData();
                fail("Administrative connection should fail when all writer nodes reject the connection.");
            } catch (SQLException expected) {
                assertEquals(1, ControlledDataSource.getAttempts("writer-a"));
                assertEquals(1, ControlledDataSource.getAttempts("writer-b"));
            }
        } finally {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            invoke(dataSourceClass, dataSource, "close", new Class<?>[0], new Object[0]);
        }
    }

    private Object createDataSource(String nodeSetterName, String firstNodeName, String secondNodeName) throws Exception {
        Object rootConfig = createRootConfig(nodeSetterName, firstNodeName, secondNodeName);
        Class<?> rootConfigClass = rootConfig.getClass();
        Class<?> dataSourceClass = Class.forName("com.mysplitter.MySplitterDataSource");
        Constructor<?> constructor = dataSourceClass.getConstructor(rootConfigClass);
        return constructor.newInstance(rootConfig);
    }

    private Object createRootConfig(String nodeSetterName, String firstNodeName, String secondNodeName) throws Exception {
        Class<?> rootConfigClass = Class.forName("com.mysplitter.config.MySplitterRootConfig");
        Class<?> configClass = Class.forName("com.mysplitter.config.MySplitterConfig");
        Class<?> commonClass = Class.forName("com.mysplitter.config.MySplitterCommonConfig");
        Class<?> databaseConfigClass = Class.forName("com.mysplitter.config.MySplitterDataBaseConfig");
        Class<?> nodeConfigClass = Class.forName("com.mysplitter.config.MySplitterDataSourceNodeConfig");

        Object rootConfig = rootConfigClass.getConstructor().newInstance();
        Object mySplitterConfig = configClass.getConstructor().newInstance();
        Object commonConfig = commonClass.getConstructor().newInstance();
        Object databaseConfig = databaseConfigClass.getConstructor().newInstance();

        invoke(commonClass, commonConfig, "setDataSourceClass",
                new Class<?>[]{String.class}, new Object[]{ControlledDataSource.class.getName()});
        invoke(configClass, mySplitterConfig, "setCommon",
                new Class<?>[]{commonClass}, new Object[]{commonConfig});
        invoke(configClass, mySplitterConfig, "setReadAndWriteParser",
                new Class<?>[]{String.class}, new Object[]{"com.mysplitter.DefaultReadAndWriteParser"});

        LinkedHashMap<String, Object> nodes = new LinkedHashMap<String, Object>();
        nodes.put(firstNodeName, createNodeConfig(nodeConfigClass, firstNodeName));
        nodes.put(secondNodeName, createNodeConfig(nodeConfigClass, secondNodeName));
        invoke(databaseConfigClass, databaseConfig, nodeSetterName,
                new Class<?>[]{LinkedHashMap.class}, new Object[]{nodes});

        Map<String, Object> databases = new LinkedHashMap<String, Object>();
        databases.put("database-main", databaseConfig);
        invoke(configClass, mySplitterConfig, "setDatabases",
                new Class<?>[]{Map.class}, new Object[]{databases});

        invoke(rootConfigClass, rootConfig, "setMysplitter",
                new Class<?>[]{configClass}, new Object[]{mySplitterConfig});
        return rootConfig;
    }

    private Object createNodeConfig(Class<?> nodeConfigClass, String nodeName) throws Exception {
        Object nodeConfig = nodeConfigClass.getConstructor().newInstance();
        invoke(nodeConfigClass, nodeConfig, "setWeight", new Class<?>[]{Integer.class}, new Object[]{Integer.valueOf(1)});
        invoke(nodeConfigClass, nodeConfig, "setConfiguration",
                new Class<?>[]{Map.class}, new Object[]{createDataSourceConfiguration(nodeName)});
        return nodeConfig;
    }

    private Map<String, Object> createDataSourceConfiguration(String nodeName) {
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("nodeName", nodeName);
        configuration.put("failTimes", Integer.valueOf(10));
        return configuration;
    }

    private Object invoke(Class<?> targetClass,
                          Object target,
                          String methodName,
                          Class<?>[] parameterTypes,
                          Object[] args) throws Exception {
        Method method = targetClass.getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    public static final class ControlledDataSource implements DataSource {

        private static final ConcurrentHashMap<String, AtomicInteger> ATTEMPTS =
                new ConcurrentHashMap<String, AtomicInteger>();

        private String nodeName;

        private int failTimes;

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

        public void setFailTimes(int failTimes) {
            this.failTimes = failTimes;
        }

        @Override
        public Connection getConnection() throws SQLException {
            AtomicInteger attemptCounter = ATTEMPTS.get(nodeName);
            if (attemptCounter == null) {
                AtomicInteger newCounter = new AtomicInteger(0);
                AtomicInteger existingCounter = ATTEMPTS.putIfAbsent(nodeName, newCounter);
                attemptCounter = existingCounter == null ? newCounter : existingCounter;
            }
            int attempt = attemptCounter.incrementAndGet();
            if (attempt <= failTimes) {
                throw new SQLException("Simulated failure for node " + nodeName + " on attempt " + attempt + ".");
            }
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
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
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
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    new InvocationHandler() {
                        private boolean closed;

                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String methodName = method.getName();
                            if ("close".equals(methodName)) {
                                closed = true;
                                return null;
                            }
                            if ("isClosed".equals(methodName)) {
                                return Boolean.valueOf(closed);
                            }
                            if ("prepareStatement".equals(methodName)) {
                                return createPreparedStatementProxy();
                            }
                            if ("createStatement".equals(methodName)) {
                                return createStatementProxy();
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
                            if ("toString".equals(methodName)) {
                                return "ControlledConnection[" + currentNodeName + "]";
                            }
                            return DefaultInvocationHandler.defaultValue(method.getReturnType());
                        }
                    });
        }

        private PreparedStatement createPreparedStatementProxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    new DefaultInvocationHandler());
        }

        private Statement createStatementProxy() {
            return (Statement) Proxy.newProxyInstance(
                    Statement.class.getClassLoader(),
                    new Class<?>[]{Statement.class},
                    new DefaultInvocationHandler());
        }
    }

    private static final class DefaultInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
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
