/*
 * Copyright 2018 BerryWang1996
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mysplitter;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.lang.reflect.Method;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.ClientInfoStatus;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class MySplitterConnectionProxy implements Connection {

    private interface ConnectionOperation {

        void apply(Connection connection) throws SQLException;
    }

    private final MySplitterDataSourceManager mySplitterDataSourceManager;

    private final String username;

    private final String password;

    private final MySplitterConnectionContext connectionContext = new MySplitterConnectionContext();

    private volatile boolean closed;

    public MySplitterConnectionProxy(MySplitterDataSourceManager mySplitterDataSourceManager) {
        this(mySplitterDataSourceManager, null, null);
    }

    public MySplitterConnectionProxy(MySplitterDataSourceManager mySplitterDataSourceManager,
                                     String username,
                                     String password) {
        this.mySplitterDataSourceManager = mySplitterDataSourceManager;
        this.username = username;
        this.password = password;
    }

    private void assertOpen() throws SQLException {
        if (closed) {
            throw new SQLException("Connection is closed.");
        }
    }

    private void assertOpenForClientInfo() throws SQLClientInfoException {
        if (closed) {
            throw new SQLClientInfoException("Connection is closed.", null, 0,
                    Collections.<String, ClientInfoStatus>emptyMap());
        }
    }

    private MySplitterConnectionState getConnectionState() {
        return connectionContext.getConnectionState();
    }

    private Connection getAdministrativeConnection() throws SQLException {
        assertOpen();
        Connection connection = connectionContext.getAdministrativeConnection();
        if (connection != null) {
            return connection;
        }
        connectionContext.assertCanOpenAdministrativeConnectionInTransaction();
        connection = this.mySplitterDataSourceManager.getDefaultConnection();
        getConnectionState().apply(connection);
        connectionContext.registerAdministrativeConnection(connection);
        return connection;
    }

    private Connection getCurrentConnection() throws SQLException {
        assertOpen();
        Connection connection = connectionContext.getCurrentConnection();
        if (connection != null) {
            return connection;
        }
        return getAdministrativeConnection();
    }

    private MySplitterRouteSelection getRouteSelection(String sql) throws SQLException {
        return getRouteSelection(new MySplitterSqlWrapper(sql));
    }

    private MySplitterRouteSelection getRouteSelection(MySplitterSqlWrapper sqlWrapper) throws SQLException {
        assertOpen();
        if (username != null || password != null) {
            return this.mySplitterDataSourceManager.getRouteSelection(connectionContext, sqlWrapper, username, password);
        }
        return this.mySplitterDataSourceManager.getRouteSelection(connectionContext, sqlWrapper);
    }

    private void applyToOpenedConnections(ConnectionOperation operation) throws SQLException {
        assertOpen();
        List<Connection> connections = connectionContext.listAllConnections();
        for (Connection connection : connections) {
            operation.apply(connection);
        }
    }

    private SQLException mergeSQLException(SQLException current, SQLException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private void terminate(ConnectionOperation operation) throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        SQLException exceptionHolder = null;
        try {
            for (Connection connection : connectionContext.listAllConnections()) {
                try {
                    operation.apply(connection);
                } catch (SQLException e) {
                    exceptionHolder = mergeSQLException(exceptionHolder, e);
                }
            }
        } finally {
            connectionContext.clear();
        }
        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
    }

    private int getKnownTransactionIsolation() {
        Integer transactionIsolation = getConnectionState().getTransactionIsolation();
        return transactionIsolation == null ? Connection.TRANSACTION_NONE : transactionIsolation.intValue();
    }

    private int getKnownHoldability() throws SQLException {
        Integer holdability = getConnectionState().getHoldability();
        if (holdability != null) {
            return holdability.intValue();
        }
        return getAdministrativeConnection().getHoldability();
    }

    @Override
    public Statement createStatement() throws SQLException {
        assertOpen();
        return new MySplitterStatementProxy(this.mySplitterDataSourceManager,
                this.connectionContext,
                this.username,
                this.password,
                null,
                null,
                null,
                this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return routeSelection.getConnection().prepareStatement(sqlWrapper.getSql());
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return routeSelection.getConnection().prepareCall(sqlWrapper.getSql());
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return getAdministrativeConnection().nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        getConnectionState().setAutoCommit(autoCommit);
        if (autoCommit) {
            connectionContext.clearPinnedRoutes();
        }
        applyToOpenedConnections(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setAutoCommit(autoCommit);
            }
        });
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return getConnectionState().isAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        assertOpen();
        SQLException exceptionHolder = null;
        for (Connection connection : connectionContext.listAllConnections()) {
            try {
                connection.commit();
            } catch (SQLException e) {
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
    }

    @Override
    public void rollback() throws SQLException {
        assertOpen();
        SQLException exceptionHolder = null;
        for (Connection connection : connectionContext.listAllConnections()) {
            try {
                connection.rollback();
            } catch (SQLException e) {
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
    }

    @Override
    public void close() throws SQLException {
        terminate(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.close();
            }
        });
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return getAdministrativeConnection().getMetaData();
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        getConnectionState().setReadOnly(readOnly);
        applyToOpenedConnections(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setReadOnly(readOnly);
            }
        });
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return getConnectionState().isReadOnly();
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
        getConnectionState().setCatalog(catalog);
        applyToOpenedConnections(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setCatalog(catalog);
            }
        });
    }

    @Override
    public String getCatalog() throws SQLException {
        if (getConnectionState().hasCatalog()) {
            return getConnectionState().getCatalog();
        }
        return getAdministrativeConnection().getCatalog();
    }

    @Override
    public void setTransactionIsolation(final int level) throws SQLException {
        getConnectionState().setTransactionIsolation(level);
        applyToOpenedConnections(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setTransactionIsolation(level);
            }
        });
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        Integer transactionIsolation = getConnectionState().getTransactionIsolation();
        if (transactionIsolation != null) {
            return transactionIsolation.intValue();
        }
        return getAdministrativeConnection().getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return getCurrentConnection().getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        applyToOpenedConnections(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.clearWarnings();
            }
        });
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        assertOpen();
        return new MySplitterStatementProxy(this.mySplitterDataSourceManager,
                this.connectionContext,
                this.username,
                this.password,
                resultSetType,
                resultSetConcurrency,
                null,
                this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return routeSelection.getConnection().prepareStatement(sqlWrapper.getSql(), resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return routeSelection.getConnection().prepareCall(sqlWrapper.getSql(), resultSetType, resultSetConcurrency);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        if (getConnectionState().hasTypeMap()) {
            return getConnectionState().getTypeMap();
        }
        return getAdministrativeConnection().getTypeMap();
    }

    @Override
    public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
        getConnectionState().setTypeMap(map);
        applyToOpenedConnections(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setTypeMap(map);
            }
        });
    }

    @Override
    public void setHoldability(final int holdability) throws SQLException {
        getConnectionState().setHoldability(holdability);
        applyToOpenedConnections(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setHoldability(holdability);
            }
        });
    }

    @Override
    public int getHoldability() throws SQLException {
        Integer holdability = getConnectionState().getHoldability();
        if (holdability != null) {
            return holdability.intValue();
        }
        return getAdministrativeConnection().getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        assertOpen();
        List<Connection> connections = connectionContext.listAllConnections();
        if (connections.size() == 0) {
            throw new SQLException("No physical connection available for savepoint creation.");
        }
        MySplitterSavepointProxy mySplitterSavepointProxy = new MySplitterSavepointProxy();
        for (Connection connection : connections) {
            mySplitterSavepointProxy.putSavepoint(connection.hashCode(), connection.setSavepoint());
        }
        return mySplitterSavepointProxy;
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        assertOpen();
        List<Connection> connections = connectionContext.listAllConnections();
        if (connections.size() == 0) {
            throw new SQLException("No physical connection available for savepoint creation.");
        }
        MySplitterSavepointProxy mySplitterSavepointProxy = new MySplitterSavepointProxy(name);
        for (Connection connection : connections) {
            mySplitterSavepointProxy.putSavepoint(connection.hashCode(), connection.setSavepoint(name));
        }
        return mySplitterSavepointProxy;
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        assertOpen();
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(savepoint.getClass(), Object.class);
            MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
            for (MethodDescriptor methodDescriptor : methodDescriptors) {
                Method method = methodDescriptor.getMethod();
                if ("getSavepoint".equals(method.getName())) {
                    List<Connection> connections = this.connectionContext.listAllConnections();
                    for (Connection connection : connections) {
                        Savepoint realSavepoint = (Savepoint) method.invoke(savepoint, connection.hashCode());
                        if (realSavepoint != null) {
                            connection.rollback(realSavepoint);
                        }
                    }
                    return;
                }
            }
        } catch (Exception e) {
            throw new SQLException("Rollback savepoint failed.", e);
        }
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        assertOpen();
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(savepoint.getClass(), Object.class);
            MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
            for (MethodDescriptor methodDescriptor : methodDescriptors) {
                Method method = methodDescriptor.getMethod();
                if ("getSavepoint".equals(method.getName())) {
                    List<Connection> connections = this.connectionContext.listAllConnections();
                    for (Connection connection : connections) {
                        Savepoint realSavepoint = (Savepoint) method.invoke(savepoint, connection.hashCode());
                        if (realSavepoint != null) {
                            connection.releaseSavepoint(realSavepoint);
                        }
                    }
                    return;
                }
            }
        } catch (Exception e) {
            throw new SQLException("Release savepoint failed.", e);
        }
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        assertOpen();
        return new MySplitterStatementProxy(this.mySplitterDataSourceManager,
                this.connectionContext,
                this.username,
                this.password,
                resultSetType,
                resultSetConcurrency,
                resultSetHoldability,
                this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                              int resultSetHoldability) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return routeSelection.getConnection().prepareStatement(sqlWrapper.getSql(), resultSetType,
                resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return routeSelection.getConnection().prepareCall(sqlWrapper.getSql(), resultSetType, resultSetConcurrency,
                resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return routeSelection.getConnection().prepareStatement(sqlWrapper.getSql(), autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return routeSelection.getConnection().prepareStatement(sqlWrapper.getSql(), columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return routeSelection.getConnection().prepareStatement(sqlWrapper.getSql(), columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        return getAdministrativeConnection().createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return getAdministrativeConnection().createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return getAdministrativeConnection().createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return getAdministrativeConnection().createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        if (closed) {
            return false;
        }
        List<Connection> connections = this.connectionContext.listAllConnections();
        if (connections.size() == 0) {
            return getAdministrativeConnection().isValid(timeout);
        }
        for (Connection connection : connections) {
            if (!connection.isValid(timeout)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        assertOpenForClientInfo();
        getConnectionState().setClientInfo(name, value);
        for (Connection connection : this.connectionContext.listAllConnections()) {
            connection.setClientInfo(name, value);
        }
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        assertOpenForClientInfo();
        getConnectionState().setClientInfo(properties);
        for (Connection connection : this.connectionContext.listAllConnections()) {
            connection.setClientInfo(properties);
        }
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        if (getConnectionState().hasClientInfo()) {
            return getConnectionState().getClientInfo(name);
        }
        return getAdministrativeConnection().getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        if (getConnectionState().hasClientInfo()) {
            return getConnectionState().getClientInfo();
        }
        return getAdministrativeConnection().getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return getAdministrativeConnection().createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return getAdministrativeConnection().createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(final String schema) throws SQLException {
        getConnectionState().setSchema(schema);
        applyToOpenedConnections(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setSchema(schema);
            }
        });
    }

    @Override
    public String getSchema() throws SQLException {
        if (getConnectionState().hasSchema()) {
            return getConnectionState().getSchema();
        }
        return getAdministrativeConnection().getSchema();
    }

    @Override
    public void abort(final Executor executor) throws SQLException {
        terminate(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.abort(executor);
            }
        });
    }

    @Override
    public void setNetworkTimeout(final Executor executor, final int milliseconds) throws SQLException {
        getConnectionState().setNetworkTimeout(executor, milliseconds);
        applyToOpenedConnections(new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setNetworkTimeout(executor, milliseconds);
            }
        });
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        Integer networkTimeout = getConnectionState().getNetworkTimeoutMilliseconds();
        if (networkTimeout != null) {
            return networkTimeout.intValue();
        }
        return getAdministrativeConnection().getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface != null && iface.isInstance(this)) {
            return iface.cast(this);
        }
        Connection connection = connectionContext.getCurrentConnection();
        if (connection == null) {
            throw new SQLException("No target connection available to unwrap " + iface + ".");
        }
        return connection.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface != null && iface.isInstance(this)) {
            return true;
        }
        Connection connection = connectionContext.getCurrentConnection();
        return connection != null && connection.isWrapperFor(iface);
    }
}
