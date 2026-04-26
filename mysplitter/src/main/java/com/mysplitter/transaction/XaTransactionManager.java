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

package com.mysplitter.transaction;

import com.mysplitter.DataSourceWrapper;
import com.mysplitter.MySplitterConnectionContext;
import com.mysplitter.MySplitterRouteKey;
import com.mysplitter.config.MySplitterTransactionConfig;
import com.mysplitter.util.StringUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import javax.transaction.xa.XAResource;

public class XaTransactionManager implements GlobalTransactionManager {

    private final TransactionCoordinator transactionCoordinator;

    public XaTransactionManager(TransactionCoordinator transactionCoordinator) {
        if (transactionCoordinator == null) {
            throw new IllegalArgumentException("MySplitter transactionCoordinator is null.");
        }
        this.transactionCoordinator = transactionCoordinator;
    }

    @Override
    public String getMode() {
        return MySplitterTransactionConfig.MODE_XA;
    }

    @Override
    public void begin(MySplitterConnectionContext connectionContext) throws SQLException {
        if (StringUtil.isBlank(connectionContext.getGlobalTransactionId())) {
            SQLException closeException = closeOpenedConnections(connectionContext);
            connectionContext.clearTransactionResources();
            if (closeException != null) {
                throw closeException;
            }
        }
        ensureGlobalTransactionId(connectionContext);
    }

    @Override
    public void beforeOpenRoute(MySplitterConnectionContext connectionContext, MySplitterRouteKey routeKey)
            throws SQLException {
        if (connectionContext.isTransactionActive()) {
            ensureGlobalTransactionId(connectionContext);
        }
    }

    @Override
    public void beforeOpenAdministrativeConnection(MySplitterConnectionContext connectionContext)
            throws SQLException {
        if (connectionContext.isTransactionActive()) {
            throw new SQLFeatureNotSupportedException("MySplitter XA transactions require routed XA branches. " +
                    "Administrative connections are not supported while an XA transaction is active.");
        }
    }

    @Override
    public Connection openRouteConnection(MySplitterConnectionContext connectionContext,
                                          MySplitterRouteKey routeKey,
                                          DataSourceWrapper dataSourceWrapper,
                                          String username,
                                          String password) throws SQLException {
        Connection existing = connectionContext.getConnection(routeKey);
        if (existing != null) {
            return existing;
        }
        if (!connectionContext.isTransactionActive()) {
            return openLocalAutoCommitConnection(connectionContext, routeKey, dataSourceWrapper, username, password);
        }

        String globalTransactionId = ensureGlobalTransactionId(connectionContext);
        XaConnectionBranch existingBranch = connectionContext.getXaBranch(routeKey);
        if (existingBranch != null) {
            return existingBranch.getConnection();
        }

        String branchId = connectionContext.nextXaBranchId(routeKey);
        XaConnectionBranch xaBranch = dataSourceWrapper.openXaBranch(globalTransactionId, branchId, username, password);
        try {
            connectionContext.getConnectionState().apply(xaBranch.getConnection());
            xaBranch.getBranchTransaction().start(XAResource.TMNOFLAGS);
            transactionCoordinator.enlist(xaBranch.getBranchTransaction());
            connectionContext.registerXaBranch(routeKey, xaBranch);
            return xaBranch.getConnection();
        } catch (SQLException e) {
            cleanupFailedBranch(xaBranch, e);
            throw e;
        } catch (RuntimeException e) {
            cleanupFailedBranch(xaBranch, e);
            throw e;
        }
    }

    @Override
    public void commit(MySplitterConnectionContext connectionContext) throws SQLException {
        String globalTransactionId = connectionContext.getGlobalTransactionId();
        if (StringUtil.isBlank(globalTransactionId)) {
            return;
        }

        SQLException exceptionHolder = endBranches(connectionContext, XAResource.TMSUCCESS);
        try {
            if (exceptionHolder == null) {
                try {
                    transactionCoordinator.commit(globalTransactionId);
                } catch (SQLException e) {
                    exceptionHolder = e;
                    throw e;
                }
            } else {
                SQLException rollbackException = rollbackAfterEndFailure(globalTransactionId);
                if (rollbackException != null) {
                    exceptionHolder.addSuppressed(rollbackException);
                }
                throw exceptionHolder;
            }
        } finally {
            SQLException closeException = closeXaBranches(connectionContext);
            connectionContext.clearTransactionResources();
            if (closeException != null) {
                if (exceptionHolder != null) {
                    exceptionHolder.addSuppressed(closeException);
                } else {
                    throw closeException;
                }
            }
        }
    }

    @Override
    public void rollback(MySplitterConnectionContext connectionContext) throws SQLException {
        String globalTransactionId = connectionContext.getGlobalTransactionId();
        if (StringUtil.isBlank(globalTransactionId)) {
            return;
        }

        SQLException exceptionHolder = endBranches(connectionContext, XAResource.TMFAIL);
        try {
            transactionCoordinator.rollback(globalTransactionId);
        } catch (SQLException e) {
            exceptionHolder = mergeSQLException(exceptionHolder, e);
        } finally {
            SQLException closeException = closeXaBranches(connectionContext);
            connectionContext.clearTransactionResources();
            exceptionHolder = mergeSQLException(exceptionHolder, closeException);
        }
        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
    }

    private Connection openLocalAutoCommitConnection(MySplitterConnectionContext connectionContext,
                                                    MySplitterRouteKey routeKey,
                                                    DataSourceWrapper dataSourceWrapper,
                                                    String username,
                                                    String password) throws SQLException {
        Connection connection;
        if (username != null || password != null) {
            connection = dataSourceWrapper.getRealDataSource().getConnection(username, password);
        } else {
            connection = dataSourceWrapper.getRealDataSource().getConnection();
        }
        try {
            connectionContext.getConnectionState().apply(connection);
            connectionContext.registerConnection(routeKey, connection);
            return connection;
        } catch (SQLException e) {
            closeQuietly(connection, e);
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(connection, e);
            throw e;
        }
    }

    private String ensureGlobalTransactionId(MySplitterConnectionContext connectionContext) throws SQLException {
        String globalTransactionId = connectionContext.getGlobalTransactionId();
        if (!StringUtil.isBlank(globalTransactionId)) {
            return globalTransactionId;
        }
        globalTransactionId = transactionCoordinator.begin();
        connectionContext.setGlobalTransactionId(globalTransactionId);
        return globalTransactionId;
    }

    private SQLException endBranches(MySplitterConnectionContext connectionContext, int flags) {
        SQLException exceptionHolder = null;
        for (XaConnectionBranch xaBranch : connectionContext.listXaBranches()) {
            try {
                xaBranch.getBranchTransaction().end(flags);
            } catch (SQLException e) {
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        return exceptionHolder;
    }

    private SQLException rollbackAfterEndFailure(String globalTransactionId) {
        try {
            transactionCoordinator.rollback(globalTransactionId);
            return null;
        } catch (SQLException e) {
            return e;
        }
    }

    private SQLException closeXaBranches(MySplitterConnectionContext connectionContext) {
        SQLException exceptionHolder = null;
        for (XaConnectionBranch xaBranch : connectionContext.listXaBranches()) {
            try {
                xaBranch.close();
            } catch (SQLException e) {
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        return exceptionHolder;
    }

    private SQLException closeOpenedConnections(MySplitterConnectionContext connectionContext) {
        SQLException exceptionHolder = null;
        for (Connection connection : connectionContext.listAllConnections()) {
            try {
                connection.close();
            } catch (SQLException e) {
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        for (XaConnectionBranch xaBranch : connectionContext.listXaBranches()) {
            try {
                xaBranch.close();
            } catch (SQLException e) {
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        return exceptionHolder;
    }

    private void cleanupFailedBranch(XaConnectionBranch xaBranch, Exception originalException) {
        try {
            xaBranch.getBranchTransaction().end(XAResource.TMFAIL);
        } catch (SQLException e) {
            originalException.addSuppressed(e);
        }
        try {
            xaBranch.getBranchTransaction().rollback();
        } catch (SQLException e) {
            originalException.addSuppressed(e);
        }
        try {
            xaBranch.close();
        } catch (SQLException e) {
            originalException.addSuppressed(e);
        }
    }

    private void closeQuietly(Connection connection, Exception originalException) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeException) {
            originalException.addSuppressed(closeException);
        }
    }

    private SQLException mergeSQLException(SQLException current, SQLException next) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}
