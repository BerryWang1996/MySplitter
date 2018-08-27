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
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * 数据库连接代理
 */
public class MySplitterConnectionProxy implements Connection {

    private MySplitterDataSourceManager mySplitterDataSourceManager;

    private String username;

    private String password;

    // TODO 会不会发生内存泄漏问题？
    private MySplitterStandByExecuteHolder mySplitterStandByExecuteHolder;

    // TODO 可以完善，如果重复使用多次是否可以优化？有些方法是不是不需要connectionHolder执行
    private MySplitterConnectionHolder mySplitterConnectionHolder;

    /**
     * 数据库连接代理（不需要用户名和密码）
     *
     * @param mySplitterDataSourceManager 数据源管理器
     */
    public MySplitterConnectionProxy(MySplitterDataSourceManager mySplitterDataSourceManager) {
        this.mySplitterDataSourceManager = mySplitterDataSourceManager;
        this.mySplitterConnectionHolder = new MySplitterConnectionHolder();
        this.mySplitterStandByExecuteHolder = new MySplitterStandByExecuteHolder(this);
    }

    /**
     * 数据库连接代理（需要用户名和密码）
     *
     * @param mySplitterDataSourceManager 数据源管理器
     * @param username                    数据源用户名
     * @param password                    数据源密码
     */
    public MySplitterConnectionProxy(MySplitterDataSourceManager mySplitterDataSourceManager,
                                     String username,
                                     String password) {
        this.mySplitterDataSourceManager = mySplitterDataSourceManager;
        this.username = username;
        this.password = password;
        this.mySplitterConnectionHolder = new MySplitterConnectionHolder();
        this.mySplitterStandByExecuteHolder = new MySplitterStandByExecuteHolder(this);
    }

    /**
     * 根据sql进行解析设置当前操作真正的连接
     */
    private void setConnectionHolder(String sql) throws SQLException {
        // 根据是否设置username和password获取真正的连接
        Connection connection;
        if (username != null || password != null) {
            connection = this.mySplitterDataSourceManager.getConnection(sql, username, password);
        } else {
            connection = this.mySplitterDataSourceManager.getConnection(sql);
        }
        // 执行待执行方法
        this.mySplitterStandByExecuteHolder.executeAll(connection);
        // 在连接保持器设置当前的连接
        this.mySplitterConnectionHolder.setCurrent(connection);
    }

    private Connection getCurrentConnection() throws SQLException {
        Connection current = mySplitterConnectionHolder.getCurrent();
        if (current == null) {
            // 如果还没有设置当前的数据源，使用默认的数据源
            current = this.mySplitterDataSourceManager.getDefaultConnection();
            mySplitterConnectionHolder.setCurrent(current);
        }
        return current;
    }

    @Override
    public Statement createStatement() throws SQLException {
        // TODO Statement 未完成
        if (true) {
            throw new SQLFeatureNotSupportedException("Operation not support now!");
        }
        return new MySplitterStatementProxy(this.mySplitterDataSourceManager,
                this.mySplitterStandByExecuteHolder,
                this.mySplitterConnectionHolder,
                this.username,
                this.password,
                null,
                null,
                null);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().prepareCall(sql);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.mySplitterStandByExecuteHolder.standBy("setAutoCommit", autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return getCurrentConnection().getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.commit();
        }
    }

    @Override
    public void rollback() throws SQLException {
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.close();
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return getCurrentConnection().isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return getCurrentConnection().getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        this.mySplitterStandByExecuteHolder.standBy("setReadOnly", readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return getCurrentConnection().isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        getCurrentConnection().setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return getCurrentConnection().getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.setTransactionIsolation(level);
        }
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
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.clearWarnings();
        }
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        // TODO Statement 未完成
        if (true) {
            throw new SQLFeatureNotSupportedException("Operation not support now!");
        }
        return new MySplitterStatementProxy(this.mySplitterDataSourceManager,
                this.mySplitterStandByExecuteHolder,
                this.mySplitterConnectionHolder,
                this.username,
                this.password,
                resultSetType,
                resultSetConcurrency,
                null);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws
            SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return getCurrentConnection().getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        getCurrentConnection().setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.setHoldability(holdability);
        }
    }

    @Override
    public int getHoldability() throws SQLException {
        return getCurrentConnection().getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        MySplitterSavepointProxy mySplitterSavepointProxy = new MySplitterSavepointProxy();
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            mySplitterSavepointProxy.putSavepoint(connection.hashCode(), connection.setSavepoint());
        }
        return mySplitterSavepointProxy;
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        MySplitterSavepointProxy mySplitterSavepointProxy = new MySplitterSavepointProxy(name);
        List<Connection> connections = mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            mySplitterSavepointProxy.putSavepoint(connection.hashCode(), connection.setSavepoint(name));
        }
        return mySplitterSavepointProxy;
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(savepoint.getClass(), Object.class);
            MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
            for (MethodDescriptor methodDescriptor : methodDescriptors) {
                Method method = methodDescriptor.getMethod();
                if ("getSavepoint".equals(method.getName())) {
                    // 获取所有的连接
                    List<Connection> connections = this.mySplitterConnectionHolder.listAll();
                    for (Connection connection : connections) {
                        // 执行获取连接方法
                        Savepoint realSavepoint = (Savepoint) method.invoke(savepoint, connection.hashCode());
                        if (realSavepoint != null) {
                            connection.rollback(realSavepoint);
                        }
                    }
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(savepoint.getClass(), Object.class);
            MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
            for (MethodDescriptor methodDescriptor : methodDescriptors) {
                Method method = methodDescriptor.getMethod();
                if ("getSavepoint".equals(method.getName())) {
                    // 获取所有的连接
                    List<Connection> connections = this.mySplitterConnectionHolder.listAll();
                    for (Connection connection : connections) {
                        // 执行获取连接方法
                        Savepoint realSavepoint = (Savepoint) method.invoke(savepoint, connection.hashCode());
                        if (realSavepoint != null) {
                            connection.releaseSavepoint(realSavepoint);
                        }
                    }
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws
            SQLException {
        // TODO Statement 未完成
        if (true) {
            throw new SQLFeatureNotSupportedException("Operation not support now!");
        }
        return new MySplitterStatementProxy(this.mySplitterDataSourceManager,
                this.mySplitterStandByExecuteHolder,
                this.mySplitterConnectionHolder,
                this.username,
                this.password,
                resultSetType,
                resultSetConcurrency,
                resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int
            resultSetHoldability) throws SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().prepareStatement(sql, resultSetType,
                resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int
            resultSetHoldability) throws SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().prepareCall(sql, resultSetType, resultSetConcurrency,
                resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        setConnectionHolder(sql);
        return getCurrentConnection().prepareStatement(sql, columnNames);
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
        List<Connection> connections = this.mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            if (!connection.isValid(timeout)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        List<Connection> connections = this.mySplitterConnectionHolder.listAll();
        for (Connection connection : connections) {
            connection.setClientInfo(name, value);
        }
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
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
    public void setSchema(String schema) throws SQLException {
        getCurrentConnection().setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return getCurrentConnection().getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        this.mySplitterStandByExecuteHolder.standBy("abort", executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        this.mySplitterStandByExecuteHolder.standBy("setNetworkTimeout", executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return getCurrentConnection().getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return getCurrentConnection().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return getCurrentConnection().isWrapperFor(iface);
    }
}
