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

import java.sql.*;

/**
 * Statement代理
 */
public class MySplitterStatementProxy implements Statement {

    private MySplitterDataSourceManager mySplitterDataSourceManager;

    private String username;

    private String password;

    private Integer resultSetType;

    private Integer resultSetConcurrency;

    private Integer resultSetHoldability;

    private MySplitterConnectionHolder mySplitterConnectionHolder;

    // TODO 会不会发生内存泄漏问题？
    private MySplitterStandByExecuteHolder mySplitterStatementProxyStandByExecuteHolder;

    // TODO 会不会发生内存泄漏问题？
    private MySplitterStandByExecuteHolder mySplitterConnectionProxyStandByExecuteHolder;

    /**
     * Statement代理
     *
     * @param mySplitterDataSourceManager                   数据源管理器
     * @param mySplitterConnectionProxyStandByExecuteHolder 数据源连接代理待执行方法保持器
     * @param mySplitterConnectionHolder                    数据源连接保持器
     * @param username                                      用户名
     * @param password                                      密码
     * @param resultSetType                                 resultSetType
     * @param resultSetConcurrency                          resultSetConcurrency
     * @param resultSetHoldability                          resultSetHoldability
     */
    public MySplitterStatementProxy(MySplitterDataSourceManager mySplitterDataSourceManager,
                                    MySplitterStandByExecuteHolder mySplitterConnectionProxyStandByExecuteHolder,
                                    MySplitterConnectionHolder mySplitterConnectionHolder,
                                    String username,
                                    String password,
                                    Integer resultSetType,
                                    Integer resultSetConcurrency,
                                    Integer resultSetHoldability) {
        this.mySplitterDataSourceManager = mySplitterDataSourceManager;
        this.mySplitterConnectionHolder = mySplitterConnectionHolder;
        this.mySplitterConnectionProxyStandByExecuteHolder = mySplitterConnectionProxyStandByExecuteHolder;
        this.mySplitterStatementProxyStandByExecuteHolder = new MySplitterStandByExecuteHolder(this);
        this.username = username;
        this.password = password;
        this.resultSetType = resultSetType;
        this.resultSetConcurrency = resultSetConcurrency;
        this.resultSetHoldability = resultSetHoldability;
    }

    /**
     * 根据sql进行解析设置当前操作真正的连接
     *
     * @param sql sql
     */
    private void setConnectionHolder(MySplitterSqlWrapper sql) throws SQLException {
        // 根据是否设置username和password获取真正的连接
        Connection connection;
        if (username != null || password != null) {
            connection = this.mySplitterDataSourceManager.getConnection(sql, username, password);
        } else {
            connection = this.mySplitterDataSourceManager.getConnection(sql);
        }
        // 执行数据源连接待执行方法
        this.mySplitterConnectionProxyStandByExecuteHolder.executeAll(connection);
        // 执行Statement待执行方法
        this.mySplitterStatementProxyStandByExecuteHolder.executeAll(connection);
        // 在连接保持器设置当前的连接
        this.mySplitterConnectionHolder.setCurrent(connection);
    }

    /**
     * 根据构造器所传参数获取Statement
     *
     * @return statement wrapper
     */
    private Statement getStatement() throws SQLException {
        // TODO 是否也应该设置一个StatementHolder？
        if (this.resultSetHoldability != null) {
            return this.mySplitterConnectionHolder.getCurrent().createStatement(this.resultSetType,
                    this.resultSetConcurrency,
                    this.resultSetHoldability);
        } else if (this.resultSetConcurrency != null || this.resultSetType != null) {
            return this.mySplitterConnectionHolder.getCurrent().createStatement(this.resultSetType,
                    this.resultSetConcurrency);
        } else {
            return this.mySplitterConnectionHolder.getCurrent().createStatement();
        }
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        setConnectionHolder(sqlWrapper);
        return getStatement().executeQuery(sqlWrapper.getSql());
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        setConnectionHolder(sqlWrapper);
        return getStatement().executeUpdate(sqlWrapper.getSql());
    }

    @Override
    public void close() throws SQLException {
        // TODO Statement 未完成
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return getStatement().getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        // TODO Statement 未完成
    }

    @Override
    public int getMaxRows() throws SQLException {
        return getStatement().getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        // TODO Statement 未完成
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        // TODO Statement 未完成
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return getStatement().getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        // TODO Statement 未完成
    }

    @Override
    public void cancel() throws SQLException {
        // TODO Statement 未完成
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return getStatement().getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        // TODO Statement 未完成
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        // TODO Statement 未完成
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        // TODO Statement 未完成
        return false;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        // TODO Statement 未完成
        return null;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        // TODO Statement 未完成
        return 0;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        // TODO Statement 未完成
        return false;
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        // TODO Statement 未完成

    }

    @Override
    public int getFetchDirection() throws SQLException {
        // TODO Statement 未完成
        return 0;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        // TODO Statement 未完成

    }

    @Override
    public int getFetchSize() throws SQLException {
        // TODO Statement 未完成
        return 0;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        // TODO Statement 未完成
        return 0;
    }

    @Override
    public int getResultSetType() throws SQLException {
        // TODO Statement 未完成
        return 0;
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        // TODO Statement 未完成

    }

    @Override
    public void clearBatch() throws SQLException {
        // TODO Statement 未完成

    }

    @Override
    public int[] executeBatch() throws SQLException {
        // TODO Statement 未完成
        return new int[0];
    }

    @Override
    public Connection getConnection() throws SQLException {
        // TODO Statement 未完成
        return null;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        // TODO Statement 未完成
        return false;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        // TODO Statement 未完成
        return null;
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        // TODO Statement 未完成
        return 0;
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        // TODO Statement 未完成
        return 0;
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        // TODO Statement 未完成
        return 0;
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        // TODO Statement 未完成
        return false;
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        // TODO Statement 未完成
        return false;
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        // TODO Statement 未完成
        return false;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        // TODO Statement 未完成
        return 0;
    }

    @Override
    public boolean isClosed() throws SQLException {
        // TODO Statement 未完成
        return false;
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        // TODO Statement 未完成

    }

    @Override
    public boolean isPoolable() throws SQLException {
        // TODO Statement 未完成
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        // TODO Statement 未完成

    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        // TODO Statement 未完成
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // TODO Statement 未完成
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // TODO Statement 未完成
        return false;
    }

}
