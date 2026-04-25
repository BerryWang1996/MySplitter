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

import java.sql.SQLException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

public class XaBranchTransaction implements BranchTransaction {

    private final String globalTransactionId;

    private final String branchId;

    private final String resourceId;

    private final XAResource xaResource;

    private final Xid xid;

    private boolean readOnly;

    public XaBranchTransaction(String globalTransactionId,
                               String branchId,
                               String resourceId,
                               XAResource xaResource) {
        if (xaResource == null) {
            throw new IllegalArgumentException("MySplitter XA branch resource is null.");
        }
        this.globalTransactionId = globalTransactionId;
        this.branchId = branchId;
        this.resourceId = resourceId;
        this.xaResource = xaResource;
        this.xid = new MySplitterXid(globalTransactionId, branchId);
    }

    @Override
    public String getGlobalTransactionId() {
        return globalTransactionId;
    }

    @Override
    public String getBranchId() {
        return branchId;
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    public Xid getXid() {
        return xid;
    }

    public void start(int flags) throws SQLException {
        try {
            xaResource.start(xid, flags);
        } catch (XAException e) {
            throw toSQLException("start", e);
        }
    }

    public void end(int flags) throws SQLException {
        try {
            xaResource.end(xid, flags);
        } catch (XAException e) {
            throw toSQLException("end", e);
        }
    }

    @Override
    public void prepare() throws SQLException {
        try {
            readOnly = xaResource.prepare(xid) == XAResource.XA_RDONLY;
        } catch (XAException e) {
            throw toSQLException("prepare", e);
        }
    }

    @Override
    public void commit() throws SQLException {
        if (readOnly) {
            return;
        }
        try {
            xaResource.commit(xid, false);
        } catch (XAException e) {
            throw toSQLException("commit", e);
        }
    }

    @Override
    public void rollback() throws SQLException {
        if (readOnly) {
            return;
        }
        try {
            xaResource.rollback(xid);
        } catch (XAException e) {
            throw toSQLException("rollback", e);
        }
    }

    private SQLException toSQLException(String operation, XAException e) {
        return new SQLException("MySplitter XA branch " + operation + " failed for resource " + resourceId +
                ", branch " + branchId + ".", e);
    }
}
