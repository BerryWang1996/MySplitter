package com.mysplitter.transaction;

import com.mysplitter.MySplitterConnectionContext;
import com.mysplitter.MySplitterRouteKey;
import com.mysplitter.config.MySplitterTransactionConfig;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalTransactionManagerTest {

    @Test
    public void shouldRejectSecondRouteInsideLocalTransaction() throws Exception {
        LocalTransactionManager transactionManager = new LocalTransactionManager();
        MySplitterConnectionContext connectionContext = new MySplitterConnectionContext();
        MySplitterRouteKey firstRoute = new MySplitterRouteKey("database-a", "writers", "writer-a");
        MySplitterRouteKey secondRoute = new MySplitterRouteKey("database-b", "writers", "writer-b");

        connectionContext.getConnectionState().setAutoCommit(false);
        connectionContext.registerConnection(firstRoute, countingConnection(null, null, null, null));

        try {
            transactionManager.beforeOpenRoute(connectionContext, secondRoute);
            fail("Expected local transaction manager to reject a second physical route.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("multiple physical connections"));
        }
    }

    @Test
    public void shouldCommitEveryRegisteredConnectionAndMergeFailures() throws Exception {
        LocalTransactionManager transactionManager = new LocalTransactionManager();
        MySplitterConnectionContext connectionContext = new MySplitterConnectionContext();
        AtomicInteger firstCommits = new AtomicInteger();
        AtomicInteger secondCommits = new AtomicInteger();

        connectionContext.registerConnection(new MySplitterRouteKey("database-a", "writers", "writer-a"),
                countingConnection(firstCommits, null, new SQLException("first commit failed"), null));
        connectionContext.registerConnection(new MySplitterRouteKey("database-b", "writers", "writer-b"),
                countingConnection(secondCommits, null, new SQLException("second commit failed"), null));

        try {
            transactionManager.commit(connectionContext);
            fail("Expected commit failures to be reported.");
        } catch (SQLException e) {
            assertEquals("first commit failed", e.getMessage());
            assertEquals(1, e.getSuppressed().length);
            assertEquals("second commit failed", e.getSuppressed()[0].getMessage());
        }
        assertEquals(1, firstCommits.get());
        assertEquals(1, secondCommits.get());
    }

    @Test
    public void shouldRollbackEveryRegisteredConnection() throws Exception {
        LocalTransactionManager transactionManager = new LocalTransactionManager();
        MySplitterConnectionContext connectionContext = new MySplitterConnectionContext();
        AtomicInteger firstRollbacks = new AtomicInteger();
        AtomicInteger secondRollbacks = new AtomicInteger();

        connectionContext.registerConnection(new MySplitterRouteKey("database-a", "writers", "writer-a"),
                countingConnection(null, firstRollbacks, null, null));
        connectionContext.registerConnection(new MySplitterRouteKey("database-b", "writers", "writer-b"),
                countingConnection(null, secondRollbacks, null, null));

        transactionManager.rollback(connectionContext);

        assertEquals(1, firstRollbacks.get());
        assertEquals(1, secondRollbacks.get());
    }

    @Test
    public void shouldCreateUnsupportedXaManagerUntilXaRuntimeLands() throws Exception {
        MySplitterTransactionConfig transactionConfig = new MySplitterTransactionConfig();
        transactionConfig.setMode("XA");

        GlobalTransactionManager transactionManager = TransactionManagers.create(transactionConfig);

        assertEquals("xa", transactionManager.getMode());
        try {
            transactionManager.begin(new MySplitterConnectionContext());
            fail("Expected XA transaction manager placeholder to fail clearly.");
        } catch (SQLFeatureNotSupportedException e) {
            assertTrue(e.getMessage().contains("xa is planned but not implemented yet"));
        }
    }

    @Test
    public void shouldCreateXaManagerWhenRuntimeRegistryIsSupplied() throws Exception {
        MySplitterTransactionConfig transactionConfig = new MySplitterTransactionConfig();
        transactionConfig.setMode("XA");

        GlobalTransactionManager transactionManager =
                TransactionManagers.create(transactionConfig, new XaResourceRegistry());

        assertEquals("xa", transactionManager.getMode());
    }

    private Connection countingConnection(final AtomicInteger commits,
                                          final AtomicInteger rollbacks,
                                          final SQLException commitException,
                                          final SQLException rollbackException) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String methodName = method.getName();
                        if ("commit".equals(methodName)) {
                            if (commits != null) {
                                commits.incrementAndGet();
                            }
                            if (commitException != null) {
                                throw commitException;
                            }
                            return null;
                        }
                        if ("rollback".equals(methodName)) {
                            if (rollbacks != null) {
                                rollbacks.incrementAndGet();
                            }
                            if (rollbackException != null) {
                                throw rollbackException;
                            }
                            return null;
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
