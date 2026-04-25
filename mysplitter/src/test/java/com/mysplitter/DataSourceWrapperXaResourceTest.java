package com.mysplitter;

import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.transaction.XaResourceDescriptor;
import org.junit.Test;

import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

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

        @Override
        public XAConnection getXAConnection() throws SQLException {
            return null;
        }

        @Override
        public XAConnection getXAConnection(String user, String password) throws SQLException {
            return null;
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
}
