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

    private final MySplitterStandByExecuteHolder mySplitterStandByExecuteHolder;

    private final MySplitterConnectionHolder mySplitterConnectionHolder;

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
        this.mySplitterConnectionHolder = new MySplitterConnectionHolder();
        this.mySplitterStandByExecuteHolder = new MySplitterStandByExecuteHolder(this);
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

    private void setConnectionHolder(MySplitterSqlWrapper sql) throws SQLException {
        assertOpen();
        Connection connection = getTargetConnection(sql);
        this.mySplitterStandByExecuteHolder.executeAll(connection);
        this.mySplitterConnectionHolder.setCurrent(connection);
    }

    private Connection getTargetConnection(MySplitterSqlWrapper sql) throws SQLException {
        assertOpen();
        if (username != null || password != null) {
            return this.mySplitterDataSourceManager.getConnection(sql, username, password);
        }
        return this.mySplitterDataSourceManager.getConnection(sql);
    }

    private Connection getCurrentConnection() throws SQLException {
        assertOpen();
        Connection connection = mySplitterConnectionHolder.getCurrent();
        if (connection == null) {
            connection = this.mySplitterDataSourceManager.getDefaultConnection();
            this.mySplitterConnectionHolder.setCurrent(connection);
        }
        this.mySplitterStandByExecuteHolder.executeAll(connection);
        return connection;
    }

    private void recordAndApply(String methodName,
                                ConnectionOperation operation,
                                Object... params) throws SQLException {
        assertOpen();
        this.mySplitterStandByExecuteHolder.standBy(methodName, params);
        List<Connection> connections = this.mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            operation.apply(connection);
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        assertOpen();
        return new MySplitterStatementProxy(this.mySplitterDataSourceManager,
                this.mySplitterStandByExecuteHolder,
                this.mySplitterConnectionHolder,
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
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().prepareStatement(sqlWrapper.getSql());
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().prepareCall(sqlWrapper.getSql());
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().nativeSQL(sqlWrapper.getSql());
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        recordAndApply("setAutoCommit", new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setAutoCommit(autoCommit);
            }
        }, autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return getCurrentConnection().getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        assertOpen();
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.commit();
        }
    }

    @Override
    public void rollback() throws SQLException {
        assertOpen();
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            mySplitterConnectionHolder.clearAll();
        } finally {
            mySplitterStandByExecuteHolder.releaseAll();
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return getCurrentConnection().getMetaData();
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        recordAndApply("setReadOnly", new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setReadOnly(readOnly);
            }
        }, readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return getCurrentConnection().isReadOnly();
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
        recordAndApply("setCatalog", new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setCatalog(catalog);
            }
        }, catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return getCurrentConnection().getCatalog();
    }

    @Override
    public void setTransactionIsolation(final int level) throws SQLException {
        recordAndApply("setTransactionIsolation", new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setTransactionIsolation(level);
            }
        }, level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return getCurrentConnection().getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return getCurrentConnection().getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        assertOpen();
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.clearWarnings();
        }
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        assertOpen();
        return new MySplitterStatementProxy(this.mySplitterDataSourceManager,
                this.mySplitterStandByExecuteHolder,
                this.mySplitterConnectionHolder,
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
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().prepareStatement(sqlWrapper.getSql(), resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().prepareCall(sqlWrapper.getSql(), resultSetType, resultSetConcurrency);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return getCurrentConnection().getTypeMap();
    }

    @Override
    public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
        recordAndApply("setTypeMap", new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setTypeMap(map);
            }
        }, map);
    }

    @Override
    public void setHoldability(final int holdability) throws SQLException {
        recordAndApply("setHoldability", new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setHoldability(holdability);
            }
        }, holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return getCurrentConnection().getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        assertOpen();
        MySplitterSavepointProxy mySplitterSavepointProxy = new MySplitterSavepointProxy();
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            mySplitterSavepointProxy.putSavepoint(connection.hashCode(), connection.setSavepoint());
        }
        return mySplitterSavepointProxy;
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        assertOpen();
        MySplitterSavepointProxy mySplitterSavepointProxy = new MySplitterSavepointProxy(name);
        List<Connection> connections = mySplitterConnectionHolder.listAll();
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
                    List<Connection> connections = this.mySplitterConnectionHolder.listAll();
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
                    List<Connection> connections = this.mySplitterConnectionHolder.listAll();
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
                this.mySplitterStandByExecuteHolder,
                this.mySplitterConnectionHolder,
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
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().prepareStatement(sqlWrapper.getSql(), resultSetType,
                resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().prepareCall(sqlWrapper.getSql(), resultSetType, resultSetConcurrency,
                resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().prepareStatement(sqlWrapper.getSql(), autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().prepareStatement(sqlWrapper.getSql(), columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        setConnectionHolder(sqlWrapper);
        return getCurrentConnection().prepareStatement(sqlWrapper.getSql(), columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        return getCurrentConnection().createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return getCurrentConnection().createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return getCurrentConnection().createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return getCurrentConnection().createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        if (closed) {
            return false;
        }
        List<Connection> connections = this.mySplitterConnectionHolder.listAll();
        if (connections.size() == 0) {
            return getCurrentConnection().isValid(timeout);
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
        this.mySplitterStandByExecuteHolder.standBy("setClientInfo", name, value);
        List<Connection> connections = this.mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.setClientInfo(name, value);
        }
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        assertOpenForClientInfo();
        this.mySplitterStandByExecuteHolder.standBy("setClientInfo", properties);
        List<Connection> connections = this.mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.setClientInfo(properties);
        }
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return getCurrentConnection().getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return getCurrentConnection().getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return getCurrentConnection().createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return getCurrentConnection().createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(final String schema) throws SQLException {
        recordAndApply("setSchema", new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setSchema(schema);
            }
        }, schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return getCurrentConnection().getSchema();
    }

    @Override
    public void abort(final Executor executor) throws SQLException {
        recordAndApply("abort", new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.abort(executor);
            }
        }, executor);
    }

    @Override
    public void setNetworkTimeout(final Executor executor, final int milliseconds) throws SQLException {
        recordAndApply("setNetworkTimeout", new ConnectionOperation() {
            @Override
            public void apply(Connection connection) throws SQLException {
                connection.setNetworkTimeout(executor, milliseconds);
            }
        }, executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return getCurrentConnection().getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface != null && iface.isInstance(this)) {
            return iface.cast(this);
        }
        Connection connection = mySplitterConnectionHolder.getCurrent();
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
        Connection connection = mySplitterConnectionHolder.getCurrent();
        return connection != null && connection.isWrapperFor(iface);
    }
}
