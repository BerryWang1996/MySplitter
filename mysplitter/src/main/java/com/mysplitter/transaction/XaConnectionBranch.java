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

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.XAConnection;

public class XaConnectionBranch implements AutoCloseable {

    private final XAConnection xaConnection;

    private final Connection connection;

    private final XaBranchTransaction branchTransaction;

    public XaConnectionBranch(XAConnection xaConnection,
                              Connection connection,
                              XaBranchTransaction branchTransaction) {
        if (xaConnection == null) {
            throw new IllegalArgumentException("MySplitter XA connection is null.");
        }
        if (connection == null) {
            throw new IllegalArgumentException("MySplitter XA physical connection is null.");
        }
        if (branchTransaction == null) {
            throw new IllegalArgumentException("MySplitter XA branch transaction is null.");
        }
        this.xaConnection = xaConnection;
        this.connection = connection;
        this.branchTransaction = branchTransaction;
    }

    public XAConnection getXaConnection() {
        return xaConnection;
    }

    public Connection getConnection() {
        return connection;
    }

    public XaBranchTransaction getBranchTransaction() {
        return branchTransaction;
    }

    @Override
    public void close() throws SQLException {
        SQLException exceptionHolder = null;
        try {
            connection.close();
        } catch (SQLException e) {
            exceptionHolder = mergeSQLException(exceptionHolder, e);
        }
        try {
            xaConnection.close();
        } catch (SQLException e) {
            exceptionHolder = mergeSQLException(exceptionHolder, e);
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
}
