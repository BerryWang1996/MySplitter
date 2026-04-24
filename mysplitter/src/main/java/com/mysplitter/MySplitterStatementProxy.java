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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public class MySplitterStatementProxy implements Statement {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySplitterStatementProxy.class);

    private interface StatementOperation {

        void apply(Statement statement) throws SQLException;
    }

    private final MySplitterDataSourceManager mySplitterDataSourceManager;

    private final String username;

    private final String password;

    private final Integer resultSetType;

    private final Integer resultSetConcurrency;

    private final Integer resultSetHoldability;

    private final MySplitterConnectionContext connectionContext;

    private final Map<MySplitterRouteKey, Statement> statements =
            new LinkedHashMap<MySplitterRouteKey, Statement>();

    private final MySplitterStandByExecuteHolder statementStandByExecuteHolder;

    private final Connection logicalConnection;

    private volatile boolean closed;

    private volatile MySplitterRouteKey currentRouteKey;

    public MySplitterStatementProxy(MySplitterDataSourceManager mySplitterDataSourceManager,
                                    MySplitterConnectionContext connectionContext,
                                    String username,
                                    String password,
                                    Integer resultSetType,
                                    Integer resultSetConcurrency,
                                    Integer resultSetHoldability,
                                    Connection logicalConnection) {
        this.mySplitterDataSourceManager = mySplitterDataSourceManager;
        this.connectionContext = connectionContext;
        this.statementStandByExecuteHolder = new MySplitterStandByExecuteHolder(this);
        this.username = username;
        this.password = password;
        this.resultSetType = resultSetType;
        this.resultSetConcurrency = resultSetConcurrency;
        this.resultSetHoldability = resultSetHoldability;
        this.logicalConnection = logicalConnection;
    }

    private void assertOpen() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed.");
        }
    }

    private Statement getCurrentStatement() throws SQLException {
        assertOpen();
        if (currentRouteKey == null) {
            throw new SQLException("Statement has no target statement yet.");
        }
        Statement statement = statements.get(currentRouteKey);
        if (statement == null) {
            throw new SQLException("Statement has no target statement yet.");
        }
        return statement;
    }

    private Statement getStatement(MySplitterRouteSelection routeSelection) throws SQLException {
        Statement statement = statements.get(routeSelection.getRouteKey());
        if (statement != null) {
            try {
                if (!statement.isClosed() && statement.getConnection() == routeSelection.getConnection()) {
                    currentRouteKey = routeSelection.getRouteKey();
                    return statement;
                }
            } catch (SQLException e) {
                LOGGER.debug("Discarding stale statement after validation failed.", e);
            }
        }

        Connection connection = routeSelection.getConnection();
        if (this.resultSetHoldability != null) {
            statement = connection.createStatement(this.resultSetType,
                    this.resultSetConcurrency, this.resultSetHoldability);
        } else if (this.resultSetConcurrency != null || this.resultSetType != null) {
            statement = connection.createStatement(this.resultSetType, this.resultSetConcurrency);
        } else {
            statement = connection.createStatement();
        }
        statements.put(routeSelection.getRouteKey(), statement);
        currentRouteKey = routeSelection.getRouteKey();
        this.statementStandByExecuteHolder.executeAll(statement);
        return statement;
    }

    private MySplitterRouteSelection getRouteSelection(MySplitterSqlWrapper sqlWrapper) throws SQLException {
        assertOpen();
        if (username != null || password != null) {
            return this.mySplitterDataSourceManager.getRouteSelection(connectionContext, sqlWrapper, username, password);
        }
        return this.mySplitterDataSourceManager.getRouteSelection(connectionContext, sqlWrapper);
    }

    private void recordAndApply(String methodName,
                                StatementOperation operation,
                                Object... params) throws SQLException {
        assertOpen();
        this.statementStandByExecuteHolder.standBy(methodName, params);
        for (Statement statement : this.statements.values()) {
            operation.apply(statement);
        }
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return getStatement(routeSelection).executeQuery(sqlWrapper.getSql());
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return getStatement(routeSelection).executeUpdate(sqlWrapper.getSql());
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        SQLException exceptionHolder = null;
        try {
            for (Statement statement : this.statements.values()) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    if (exceptionHolder == null) {
                        exceptionHolder = e;
                    } else {
                        exceptionHolder.addSuppressed(e);
                    }
                }
            }
        } finally {
            this.statements.clear();
            this.statementStandByExecuteHolder.releaseAll();
        }
        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return getCurrentStatement().getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(final int max) throws SQLException {
        recordAndApply("setMaxFieldSize", new StatementOperation() {
            @Override
            public void apply(Statement statement) throws SQLException {
                statement.setMaxFieldSize(max);
            }
        }, max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return getCurrentStatement().getMaxRows();
    }

    @Override
    public void setMaxRows(final int max) throws SQLException {
        recordAndApply("setMaxRows", new StatementOperation() {
            @Override
            public void apply(Statement statement) throws SQLException {
                statement.setMaxRows(max);
            }
        }, max);
    }

    @Override
    public void setEscapeProcessing(final boolean enable) throws SQLException {
        recordAndApply("setEscapeProcessing", new StatementOperation() {
            @Override
            public void apply(Statement statement) throws SQLException {
                statement.setEscapeProcessing(enable);
            }
        }, enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return getCurrentStatement().getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(final int seconds) throws SQLException {
        recordAndApply("setQueryTimeout", new StatementOperation() {
            @Override
            public void apply(Statement statement) throws SQLException {
                statement.setQueryTimeout(seconds);
            }
        }, seconds);
    }

    @Override
    public void cancel() throws SQLException {
        assertOpen();
        SQLException sqlException = null;
        for (Statement statement : this.statements.values()) {
            try {
                statement.cancel();
            } catch (SQLException e) {
                if (sqlException == null) {
                    sqlException = e;
                } else {
                    sqlException.addSuppressed(e);
                }
            }
        }
        if (sqlException != null) {
            throw sqlException;
        }
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return getCurrentStatement().getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        assertOpen();
        SQLException sqlException = null;
        for (Statement statement : this.statements.values()) {
            try {
                statement.clearWarnings();
            } catch (SQLException e) {
                if (sqlException == null) {
                    sqlException = e;
                } else {
                    sqlException.addSuppressed(e);
                }
            }
        }
        if (sqlException != null) {
            throw sqlException;
        }
    }

    @Override
    public void setCursorName(final String name) throws SQLException {
        recordAndApply("setCursorName", new StatementOperation() {
            @Override
            public void apply(Statement statement) throws SQLException {
                statement.setCursorName(name);
            }
        }, name);
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return getStatement(routeSelection).execute(sqlWrapper.getSql());
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        Statement statement = currentRouteKey == null ? null : statements.get(currentRouteKey);
        return statement == null ? null : statement.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        Statement statement = currentRouteKey == null ? null : statements.get(currentRouteKey);
        return statement == null ? -1 : statement.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        Statement statement = currentRouteKey == null ? null : statements.get(currentRouteKey);
        return statement != null && statement.getMoreResults();
    }

    @Override
    public void setFetchDirection(final int direction) throws SQLException {
        recordAndApply("setFetchDirection", new StatementOperation() {
            @Override
            public void apply(Statement statement) throws SQLException {
                statement.setFetchDirection(direction);
            }
        }, direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return getCurrentStatement().getFetchDirection();
    }

    @Override
    public void setFetchSize(final int rows) throws SQLException {
        recordAndApply("setFetchSize", new StatementOperation() {
            @Override
            public void apply(Statement statement) throws SQLException {
                statement.setFetchSize(rows);
            }
        }, rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return getCurrentStatement().getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return getCurrentStatement().getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return getCurrentStatement().getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        getStatement(routeSelection).addBatch(sqlWrapper.getSql());
    }

    @Override
    public void clearBatch() throws SQLException {
        getCurrentStatement().clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        return getCurrentStatement().executeBatch();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return logicalConnection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        Statement statement = currentRouteKey == null ? null : statements.get(currentRouteKey);
        return statement != null && statement.getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return getCurrentStatement().getGeneratedKeys();
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return getStatement(routeSelection).executeUpdate(sqlWrapper.getSql(), autoGeneratedKeys);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return getStatement(routeSelection).executeUpdate(sqlWrapper.getSql(), columnIndexes);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return getStatement(routeSelection).executeUpdate(sqlWrapper.getSql(), columnNames);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return getStatement(routeSelection).execute(sqlWrapper.getSql(), autoGeneratedKeys);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return getStatement(routeSelection).execute(sqlWrapper.getSql(), columnIndexes);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        MySplitterSqlWrapper sqlWrapper = new MySplitterSqlWrapper(sql);
        MySplitterRouteSelection routeSelection = getRouteSelection(sqlWrapper);
        return getStatement(routeSelection).execute(sqlWrapper.getSql(), columnNames);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return getCurrentStatement().getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        if (closed) {
            return true;
        }
        if (this.statements.size() == 0) {
            return false;
        }
        for (Statement statement : this.statements.values()) {
            if (!statement.isClosed()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setPoolable(final boolean poolable) throws SQLException {
        recordAndApply("setPoolable", new StatementOperation() {
            @Override
            public void apply(Statement statement) throws SQLException {
                statement.setPoolable(poolable);
            }
        }, poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return getCurrentStatement().isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        recordAndApply("closeOnCompletion", new StatementOperation() {
            @Override
            public void apply(Statement statement) throws SQLException {
                statement.closeOnCompletion();
            }
        });
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return getCurrentStatement().isCloseOnCompletion();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface != null && iface.isInstance(this)) {
            return iface.cast(this);
        }
        Statement statement = currentRouteKey == null ? null : this.statements.get(currentRouteKey);
        if (statement == null) {
            throw new SQLException("No target statement available to unwrap " + iface + ".");
        }
        return statement.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface != null && iface.isInstance(this)) {
            return true;
        }
        Statement statement = currentRouteKey == null ? null : this.statements.get(currentRouteKey);
        return statement != null && statement.isWrapperFor(iface);
    }
}
