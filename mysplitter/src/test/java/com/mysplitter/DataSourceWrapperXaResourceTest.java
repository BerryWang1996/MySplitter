package com.mysplitter;

import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.transaction.XaConnectionBranch;
import com.mysplitter.transaction.XaResourceDescriptor;
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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.transaction.xa.XAResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataSourceWrapperXaResourceTest {

    @Test
    public void shouldExposeUnavailableXaDescriptorWhenNoXaConfigurationExists() throws Exception {
        DataSourceWrapper wrapper = createWrapper(configuration());

        wrapper.initRealDataSource();

        XaResourceDescriptor descriptor = wrapper.getXaResourceDescriptor();
        assertFalse(wrapper.isXaCapable());
        assertEquals("database-main:writers:writer-node", descriptor.getResourceId());
        assertEquals(PlainDataSource.class.getName(), descriptor.getDataSourceClassName());
        assertNull(descriptor.getXaDataSourceClassName());
        assertTrue(descriptor.getUnavailableReason().contains("No xaDataSourceClass"));
    }

    @Test
    public void shouldExposeConfiguredXaDataSourceClass() throws Exception {
        Map<String, Object> configuration = configuration();
        configuration.put("xaDataSourceClass", DemoXaDataSource.class.getName());
        DataSourceWrapper wrapper = createWrapper(configuration);

        wrapper.initRealDataSource();

        XaResourceDescriptor descriptor = wrapper.getXaResourceDescriptor();
        assertTrue(wrapper.isXaCapable());
        assertEquals(DemoXaDataSource.class.getName(), descriptor.getXaDataSourceClassName());
        assertEquals("database-main:writers:writer-node", descriptor.getResourceId());
    }

    @Test
    public void shouldRejectConfiguredXaDataSourceClassThatDoesNotImplementXaDataSource() throws Exception {
        Map<String, Object> configuration = configuration();
        configuration.put("xaDataSourceClass", String.class.getName());
        DataSourceWrapper wrapper = createWrapper(configuration);

        try {
            wrapper.initRealDataSource();
            fail("Expected invalid xaDataSourceClass to be rejected.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not implement javax.sql.XADataSource"));
        }
    }

    @Test
    public void shouldDetectXaDataSourceByUnwrap() throws Exception {
        Map<String, Object> configuration = configuration();
        configuration.put("dataSourceClass", XaUnwrappingDataSource.class.getName());
        DataSourceWrapper wrapper = createWrapper(configuration);

        wrapper.initRealDataSource();

        XaResourceDescriptor descriptor = wrapper.getXaResourceDescriptor();
        assertTrue(wrapper.isXaCapable());
        assertEquals(DemoXaDataSource.class.getName(), descriptor.getXaDataSourceClassName());
    }

    @Test
    public void shouldOpenXaBranchFromConfiguredXaDataSourceClass() throws Exception {
        DemoXaDataSource.reset();
        Map<String, Object> configuration = configuration();
        Map<String, Object> xaProperties = new HashMap<String, Object>();
        xaProperties.put("url", "jdbc:demo");
        xaProperties.put("port", Integer.valueOf(3306));
        configuration.put("xaDataSourceClass", DemoXaDataSource.class.getName());
        configuration.put("xaProperties", xaProperties);
        DataSourceWrapper wrapper = createWrapper(configuration);

        wrapper.initRealDataSource();
        XaConnectionBranch branch = wrapper.openXaBranch("global-1", "branch-1", "user-a", "secret");
        try {
            assertEquals("database-main:writers:writer-node", branch.getBranchTransaction().getResourceId());
            assertEquals("global-1", branch.getBranchTransaction().getGlobalTransactionId());
            assertEquals("branch-1", branch.getBranchTransaction().getBranchId());
            assertEquals("jdbc:demo", DemoXaDataSource.lastUrl);
            assertEquals(3306, DemoXaDataSource.lastPort);
            assertEquals("user-a", DemoXaDataSource.lastUser);
            assertEquals("secret", DemoXaDataSource.lastPassword);
        } finally {
            branch.close();
        }
        assertTrue(DemoXaDataSource.lastXaConnection.closed);
        assertTrue(DemoXaDataSource.lastXaConnection.connectionClosed);
    }

    @Test
    public void shouldRejectOpeningXaBranchWhenNodeIsNotXaCapable() throws Exception {
        DataSourceWrapper wrapper = createWrapper(configuration());

        wrapper.initRealDataSource();

        try {
            wrapper.openXaBranch("global-1", "branch-1", null, null);
            fail("Expected non-XA datasource node to reject XA branch opening.");
        } catch (SQLFeatureNotSupportedException e) {
            assertTrue(e.getMessage().contains("not XA capable"));
        }
    }

    private DataSourceWrapper createWrapper(Map<String, Object> configuration) {
        MySplitterDataSourceNodeConfig nodeConfig = new MySplitterDataSourceNodeConfig();
        nodeConfig.setDataSourceClass(String.valueOf(configuration.get("dataSourceClass")));
        nodeConfig.setConfiguration(configuration);
        return new DataSourceWrapper("writer-node", "database-main", "writers", nodeConfig, null);
    }

    private Map<String, Object> configuration() {
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("dataSourceClass", PlainDataSource.class.getName());
        return configuration;
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

    public static final class XaUnwrappingDataSource extends PlainDataSource {

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (XADataSource.class.equals(iface)) {
                return iface.cast(new DemoXaDataSource());
            }
            return super.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return XADataSource.class.equals(iface);
        }
    }

    public static final class DemoXaDataSource implements XADataSource {

        private static String lastUrl;

        private static int lastPort;

        private static String lastUser;

        private static String lastPassword;

        private static DemoXaConnection lastXaConnection;

        public static void reset() {
            lastUrl = null;
            lastPort = 0;
            lastUser = null;
            lastPassword = null;
            lastXaConnection = null;
        }

        public void setUrl(String url) {
            lastUrl = url;
        }

        public void setPort(int port) {
            lastPort = port;
        }

        @Override
        public XAConnection getXAConnection() throws SQLException {
            lastXaConnection = new DemoXaConnection();
            return lastXaConnection;
        }

        @Override
        public XAConnection getXAConnection(String user, String password) throws SQLException {
            lastUser = user;
            lastPassword = password;
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

    private static final class DemoXaConnection implements XAConnection {

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
            return (XAResource) Proxy.newProxyInstance(
                    XAResource.class.getClassLoader(),
                    new Class<?>[]{XAResource.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            return defaultValue(method.getReturnType());
                        }
                    });
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
