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

import java.sql.Connection;
import java.sql.SQLException;

public class LocalTransactionManager implements GlobalTransactionManager {

    @Override
    public String getMode() {
        return MySplitterTransactionConfig.MODE_LOCAL;
    }

    @Override
    public void begin(MySplitterConnectionContext connectionContext) throws SQLException {
        // Local mode uses the physical JDBC transaction on the selected connection.
    }

    @Override
    public void beforeOpenRoute(MySplitterConnectionContext connectionContext, MySplitterRouteKey routeKey)
            throws SQLException {
        connectionContext.assertCanOpenRouteInTransaction(routeKey);
    }

    @Override
    public void beforeOpenAdministrativeConnection(MySplitterConnectionContext connectionContext)
            throws SQLException {
        connectionContext.assertCanOpenAdministrativeConnectionInTransaction();
    }

    @Override
    public Connection openRouteConnection(MySplitterConnectionContext connectionContext,
                                          MySplitterRouteKey routeKey,
                                          DataSourceWrapper dataSourceWrapper,
                                          String username,
                                          String password) throws SQLException {
        beforeOpenRoute(connectionContext, routeKey);
        Connection connection = openConnection(dataSourceWrapper, username, password);
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

    @Override
    public void commit(MySplitterConnectionContext connectionContext) throws SQLException {
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
    public void rollback(MySplitterConnectionContext connectionContext) throws SQLException {
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

    private SQLException mergeSQLException(SQLException current, SQLException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private Connection openConnection(DataSourceWrapper dataSourceWrapper,
                                      String username,
                                      String password) throws SQLException {
        if (username != null || password != null) {
            return dataSourceWrapper.getRealDataSource().getConnection(username, password);
        }
        return dataSourceWrapper.getRealDataSource().getConnection();
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
}
