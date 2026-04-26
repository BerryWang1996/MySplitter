package com.mysplitter.transaction;

import com.mysplitter.DataSourceWrapper;
import com.mysplitter.MySplitterConnectionContext;
import com.mysplitter.MySplitterRouteKey;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import org.junit.Test;

import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class XaTransactionManagerTest {

    @Test
    public void shouldOpenAndReuseXaBranchForSameRoute() throws Exception {
        RecordingXaDataSource.reset();
        XaTransactionManager transactionManager = newTransactionManager();
        MySplitterConnectionContext connectionContext = transactionContext();
        MySplitterRouteKey routeKey = new MySplitterRouteKey("database-a", "writers", "writer-a");
        DataSourceWrapper wrapper = xaWrapper("database-a", "writers", "writer-a");

        Connection first = transactionManager.openRouteConnection(connectionContext, routeKey, wrapper, null, null);
        Connection second = transactionManager.openRouteConnection(connectionContext, routeKey, wrapper, null, null);

        assertSame(first, second);
        assertEquals(1, RecordingXaDataSource.openedConnections.size());
        assertEquals(1, RecordingXaDataSource.openedConnections.get(0).xaResource.starts);
        assertEquals(0, RecordingXaDataSource.openedConnections.get(0).xaResource.prepares);
    }

    @Test
    public void shouldEnlistTwoRoutesAndCommitThroughCoordinator() throws Exception {
        RecordingXaDataSource.reset();
        XaTransactionManager transactionManager = newTransactionManager();
        MySplitterConnectionContext connectionContext = transactionContext();

        transactionManager.openRouteConnection(connectionContext,
                new MySplitterRouteKey("database-a", "writers", "writer-a"),
                xaWrapper("database-a", "writers", "writer-a"), null, null);
        transactionManager.openRouteConnection(connectionContext,
                new MySplitterRouteKey("database-b", "writers", "writer-b"),
                xaWrapper("database-b", "writers", "writer-b"), null, null);

        transactionManager.commit(connectionContext);

        assertEquals(2, RecordingXaDataSource.openedConnections.size());
        for (RecordingXaConnection xaConnection : RecordingXaDataSource.openedConnections) {
            assertEquals(1, xaConnection.xaResource.starts);
            assertEquals(1, xaConnection.xaResource.ends);
            assertEquals(XAResource.TMSUCCESS, xaConnection.xaResource.lastEndFlag);
            assertEquals(1, xaConnection.xaResource.prepares);
            assertEquals(1, xaConnection.xaResource.commits);
            assertEquals(0, xaConnection.xaResource.rollbacks);
            assertTrue(xaConnection.connectionClosed);
            assertTrue(xaConnection.closed);
        }
        assertEquals(0, connectionContext.listAllConnections().size());
        assertNull(connectionContext.getGlobalTransactionId());
    }

    @Test
    public void shouldRollbackXaBranchesThroughCoordinator() throws Exception {
        RecordingXaDataSource.reset();
        XaTransactionManager transactionManager = newTransactionManager();
        MySplitterConnectionContext connectionContext = transactionContext();

        transactionManager.openRouteConnection(connectionContext,
                new MySplitterRouteKey("database-a", "writers", "writer-a"),
                xaWrapper("database-a", "writers", "writer-a"), null, null);
        transactionManager.openRouteConnection(connectionContext,
                new MySplitterRouteKey("database-b", "writers", "writer-b"),
                xaWrapper("database-b", "writers", "writer-b"), null, null);

        transactionManager.rollback(connectionContext);

        for (RecordingXaConnection xaConnection : RecordingXaDataSource.openedConnections) {
            assertEquals(1, xaConnection.xaResource.starts);
            assertEquals(1, xaConnection.xaResource.ends);
            assertEquals(XAResource.TMFAIL, xaConnection.xaResource.lastEndFlag);
            assertEquals(0, xaConnection.xaResource.prepares);
            assertEquals(0, xaConnection.xaResource.commits);
            assertEquals(1, xaConnection.xaResource.rollbacks);
            assertTrue(xaConnection.connectionClosed);
            assertTrue(xaConnection.closed);
        }
        assertEquals(0, connectionContext.listAllConnections().size());
        assertNull(connectionContext.getGlobalTransactionId());
    }

    @Test
    public void shouldFailBeforeOpeningWorkWhenDatasourceIsNotXaCapable() throws Exception {
        XaTransactionManager transactionManager = newTransactionManager();
        MySplitterConnectionContext connectionContext = transactionContext();

        try {
            transactionManager.openRouteConnection(connectionContext,
                    new MySplitterRouteKey("database-a", "writers", "writer-a"),
                    plainWrapper("database-a", "writers", "writer-a"), null, null);
            fail("Expected non-XA datasource to be rejected.");
        } catch (SQLFeatureNotSupportedException e) {
            assertTrue(e.getMessage().contains("not XA capable"));
        }

        assertEquals(0, connectionContext.listAllConnections().size());
    }

    @Test
    public void shouldClearAutoCommitConnectionsBeforeStartingXaTransaction() throws Exception {
        XaTransactionManager transactionManager = newTransactionManager();
        MySplitterConnectionContext connectionContext = new MySplitterConnectionContext();
        AtomicInteger closes = new AtomicInteger();
        connectionContext.registerConnection(new MySplitterRouteKey("database-a", "writers", "writer-a"),
                countingConnection(closes));

        transactionManager.begin(connectionContext);

        assertEquals(1, closes.get());
        assertEquals(0, connectionContext.listAllConnections().size());
        assertTrue(connectionContext.getGlobalTransactionId().startsWith("mysplitter-xa-"));
    }

    private XaTransactionManager newTransactionManager() {
        return new XaTransactionManager(new EmbeddedTransactionCoordinator(new InMemoryTransactionLogStore()));
    }

    private MySplitterConnectionContext transactionContext() throws SQLException {
        MySplitterConnectionContext connectionContext = new MySplitterConnectionContext();
        connectionContext.getConnectionState().setAutoCommit(false);
        return connectionContext;
    }

    private DataSourceWrapper xaWrapper(String databaseName, String nodeGroup, String nodeName) throws Exception {
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("dataSourceClass", PlainDataSource.class.getName());
        configuration.put("xaDataSourceClass", RecordingXaDataSource.class.getName());
        return initializedWrapper(databaseName, nodeGroup, nodeName, configuration);
    }

    private DataSourceWrapper plainWrapper(String databaseName, String nodeGroup, String nodeName) throws Exception {
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("dataSourceClass", PlainDataSource.class.getName());
        return initializedWrapper(databaseName, nodeGroup, nodeName, configuration);
    }

    private DataSourceWrapper initializedWrapper(String databaseName,
                                                 String nodeGroup,
                                                 String nodeName,
                                                 Map<String, Object> configuration) throws Exception {
        MySplitterDataSourceNodeConfig nodeConfig = new MySplitterDataSourceNodeConfig();
        nodeConfig.setDataSourceClass(String.valueOf(configuration.get("dataSourceClass")));
        nodeConfig.setConfiguration(configuration);
        DataSourceWrapper wrapper = new DataSourceWrapper(nodeName, databaseName, nodeGroup, nodeConfig, null);
        wrapper.initRealDataSource();
        return wrapper;
    }

    public static class PlainDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return null;
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
    }

    public static final class RecordingXaDataSource implements XADataSource {

        private static final List<RecordingXaConnection> openedConnections =
                new ArrayList<RecordingXaConnection>();

        private static void reset() {
            openedConnections.clear();
        }

        @Override
        public XAConnection getXAConnection() throws SQLException {
            RecordingXaConnection xaConnection = new RecordingXaConnection();
            openedConnections.add(xaConnection);
            return xaConnection;
        }

        @Override
        public XAConnection getXAConnection(String user, String password) throws SQLException {
            return getXAConnection();
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
    }

    private static final class RecordingXaConnection implements XAConnection {

        private final RecordingXaResource xaResource = new RecordingXaResource();

        private boolean closed;

        private boolean connectionClosed;

        @Override
        public Connection getConnection() throws SQLException {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("close".equals(method.getName())) {
                                connectionClosed = true;
                                return null;
                            }
                            if ("isClosed".equals(method.getName())) {
                                return Boolean.valueOf(connectionClosed);
                            }
                            if ("unwrap".equals(method.getName())) {
                                throw new SQLException("Not a wrapper.");
                            }
                            if ("isWrapperFor".equals(method.getName())) {
                                return Boolean.FALSE;
                            }
                            return defaultValue(method.getReturnType());
                        }
                    });
        }

        @Override
        public XAResource getXAResource() throws SQLException {
            return xaResource;
        }

        @Override
        public void close() throws SQLException {
            closed = true;
        }

        @Override
        public void addConnectionEventListener(javax.sql.ConnectionEventListener listener) {
        }

        @Override
        public void removeConnectionEventListener(javax.sql.ConnectionEventListener listener) {
        }

        @Override
        public void addStatementEventListener(javax.sql.StatementEventListener listener) {
        }

        @Override
        public void removeStatementEventListener(javax.sql.StatementEventListener listener) {
        }
    }

    private static final class RecordingXaResource implements XAResource {

        private int starts;

        private int ends;

        private int lastEndFlag;

        private int prepares;

        private int commits;

        private int rollbacks;

        @Override
        public void start(Xid xid, int flags) throws XAException {
            starts++;
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
            ends++;
            lastEndFlag = flags;
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            prepares++;
            return XAResource.XA_OK;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            commits++;
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            rollbacks++;
        }

        @Override
        public void forget(Xid xid) throws XAException {
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            return new Xid[0];
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            return xaResource == this;
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return 0;
        }

        @Override
        public boolean setTransactionTimeout(int seconds) throws XAException {
            return false;
        }
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

    private Connection countingConnection(final AtomicInteger closes) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("close".equals(method.getName())) {
                            closes.incrementAndGet();
                            return null;
                        }
                        if ("unwrap".equals(method.getName())) {
                            throw new SQLException("Not a wrapper.");
                        }
                        if ("isWrapperFor".equals(method.getName())) {
                            return Boolean.FALSE;
                        }
                        return defaultValue(method.getReturnType());
                    }
                });
    }
}
