package com.mysplitter.reflect;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MySplitterConnectionProxyReflectionTest {

    @Test
    public void shouldCloseLogicalConnectionWhenAbortHappensBeforeFirstPhysicalConnection() throws Exception {
        Object rootConfig = createRootConfig();
        Class<?> rootConfigClass = rootConfig.getClass();
        Class<?> dataSourceClass = Class.forName("com.mysplitter.MySplitterDataSource");
        Constructor<?> constructor = dataSourceClass.getConstructor(rootConfigClass);
        Object dataSource = constructor.newInstance(rootConfig);
        Connection connection = null;
        try {
            invoke(dataSourceClass, dataSource, "init", new Class<?>[0], new Object[0]);
            connection = (Connection) invoke(dataSourceClass, dataSource, "getConnection", new Class<?>[0], new Object[0]);

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
            invoke(dataSourceClass, dataSource, "close", new Class<?>[0], new Object[0]);
        }
    }

    private Object createRootConfig() throws Exception {
        Class<?> rootConfigClass = Class.forName("com.mysplitter.config.MySplitterRootConfig");
        Class<?> configClass = Class.forName("com.mysplitter.config.MySplitterConfig");
        Class<?> commonClass = Class.forName("com.mysplitter.config.MySplitterCommonConfig");
        Class<?> databaseConfigClass = Class.forName("com.mysplitter.config.MySplitterDataBaseConfig");
        Class<?> nodeConfigClass = Class.forName("com.mysplitter.config.MySplitterDataSourceNodeConfig");

        Object rootConfig = rootConfigClass.getConstructor().newInstance();
        Object mySplitterConfig = configClass.getConstructor().newInstance();
        Object commonConfig = commonClass.getConstructor().newInstance();
        Object databaseConfig = databaseConfigClass.getConstructor().newInstance();
        Object nodeConfig = nodeConfigClass.getConstructor().newInstance();

        invoke(commonClass, commonConfig, "setDataSourceClass",
                new Class<?>[]{String.class}, new Object[]{"com.zaxxer.hikari.HikariDataSource"});
        invoke(configClass, mySplitterConfig, "setCommon",
                new Class<?>[]{commonClass}, new Object[]{commonConfig});

        invoke(nodeConfigClass, nodeConfig, "setWeight", new Class<?>[]{Integer.class}, new Object[]{Integer.valueOf(1)});
        invoke(nodeConfigClass, nodeConfig, "setConfiguration",
                new Class<?>[]{Map.class}, new Object[]{createDataSourceConfiguration()});

        LinkedHashMap<String, Object> writers = new LinkedHashMap<String, Object>();
        writers.put("writer-node", nodeConfig);
        invoke(databaseConfigClass, databaseConfig, "setWriters",
                new Class<?>[]{LinkedHashMap.class}, new Object[]{writers});

        Map<String, Object> databases = new LinkedHashMap<String, Object>();
        databases.put("database-main", databaseConfig);
        invoke(configClass, mySplitterConfig, "setDatabases",
                new Class<?>[]{Map.class}, new Object[]{databases});

        invoke(rootConfigClass, rootConfig, "setMysplitter",
                new Class<?>[]{configClass}, new Object[]{mySplitterConfig});

        return rootConfig;
    }

    private Map<String, Object> createDataSourceConfiguration() {
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("jdbcUrl", "jdbc:mysql://127.0.0.1:65535/mysplitter_abort");
        configuration.put("username", "test");
        configuration.put("password", "test");
        configuration.put("driverClassName", "com.mysql.jdbc.Driver");
        configuration.put("connectionTimeout", Long.valueOf(1000L));
        return configuration;
    }

    private void assertConnectionClosed(Connection connection) throws SQLException {
        try {
            connection.createStatement();
            fail("Connection should be closed after abort.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("Connection is closed"));
        }
    }

    private Object invoke(Class<?> targetClass,
                          Object target,
                          String methodName,
                          Class<?>[] parameterTypes,
                          Object[] args) throws Exception {
        Method method = targetClass.getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }
}
