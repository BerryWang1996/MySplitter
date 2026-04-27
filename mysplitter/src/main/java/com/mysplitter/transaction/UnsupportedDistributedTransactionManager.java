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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

public class UnsupportedDistributedTransactionManager implements GlobalTransactionManager {

    private final String mode;

    public UnsupportedDistributedTransactionManager(String mode) {
        this.mode = mode;
    }

    @Override
    public String getMode() {
        return mode;
    }

    @Override
    public void begin(MySplitterConnectionContext connectionContext) throws SQLException {
        throw unsupported();
    }

    @Override
    public void beforeOpenRoute(MySplitterConnectionContext connectionContext, MySplitterRouteKey routeKey)
            throws SQLException {
        throw unsupported();
    }

    @Override
    public void beforeOpenAdministrativeConnection(MySplitterConnectionContext connectionContext)
            throws SQLException {
        throw unsupported();
    }

    @Override
    public Connection openRouteConnection(MySplitterConnectionContext connectionContext,
                                          MySplitterRouteKey routeKey,
                                          DataSourceWrapper dataSourceWrapper,
                                          String username,
                                          String password) throws SQLException {
        throw unsupported();
    }

    @Override
    public void commit(MySplitterConnectionContext connectionContext) throws SQLException {
        throw unsupported();
    }

    @Override
    public void rollback(MySplitterConnectionContext connectionContext) throws SQLException {
        throw unsupported();
    }

    @Override
    public void recover() throws SQLException {
        throw unsupported();
    }

    private SQLFeatureNotSupportedException unsupported() {
        return new SQLFeatureNotSupportedException("MySplitter transaction.mode " + mode +
                " is planned but not implemented yet.");
    }
}
