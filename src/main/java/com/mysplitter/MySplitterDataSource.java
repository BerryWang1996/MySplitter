package com.mysplitter;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class MySplitterDataSource implements DataSource, Serializable {

    private static AtomicBoolean isInited = new AtomicBoolean(false);

    public void init() {
        if (isInited.compareAndSet(false, true)) {

        }
    }

    public void close() {

    }

    public Connection getConnection() throws SQLException {
        return null;
    }

    public Connection getConnection(String username, String password) throws SQLException {
        return null;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    public void setLogWriter(PrintWriter out) throws SQLException {

    }

    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    public void setLoginTimeout(int seconds) throws SQLException {

    }

    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

}
