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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

public class XaRecoveryExecutor {

    private final TransactionLogStore transactionLogStore;

    private final XaResourceRegistry xaResourceRegistry;

    public XaRecoveryExecutor(TransactionLogStore transactionLogStore, XaResourceRegistry xaResourceRegistry) {
        if (transactionLogStore == null) {
            throw new IllegalArgumentException("MySplitter transactionLogStore is null.");
        }
        if (xaResourceRegistry == null) {
            throw new IllegalArgumentException("MySplitter xaResourceRegistry is null.");
        }
        this.transactionLogStore = transactionLogStore;
        this.xaResourceRegistry = xaResourceRegistry;
    }

    public XaRecoveryReport recover() throws SQLException {
        XaRecoveryReport report = new XaRecoveryReport();
        SQLException exceptionHolder = null;
        List<TransactionLogEntry> recoverableEntries = transactionLogStore.findRecoverable();
        for (TransactionLogEntry entry : recoverableEntries) {
            try {
                recover(entry, report);
            } catch (SQLException e) {
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
        return report;
    }

    private void recover(TransactionLogEntry entry, XaRecoveryReport report) throws SQLException {
        TransactionDecision decision = entry.getDecision();
        if (decision == null || TransactionDecision.UNKNOWN.equals(decision)) {
            report.incrementUnresolvedBranchCount();
            return;
        }

        XaRecoveryResource recoveryResource = null;
        SQLException exceptionHolder = null;
        try {
            recoveryResource = xaResourceRegistry.openRecoveryResource(entry.getResourceId());
            XAResource xaResource = recoveryResource.getXaResource();
            Xid expectedXid = new MySplitterXid(entry.getGlobalTransactionId(), entry.getBranchId());
            Xid[] recoveredXids = recoverXids(xaResource);
            if (!containsXid(recoveredXids, expectedXid)) {
                report.incrementUnresolvedBranchCount();
                return;
            }
            if (TransactionDecision.COMMIT.equals(decision)) {
                xaResource.commit(expectedXid, false);
                updateLog(entry, TransactionStatus.COMMITTED);
            } else if (TransactionDecision.ROLLBACK.equals(decision)) {
                xaResource.rollback(expectedXid);
                updateLog(entry, TransactionStatus.ROLLED_BACK);
            }
            report.incrementRecoveredBranchCount();
        } catch (XAException e) {
            exceptionHolder = toRecoverySQLException(entry, e);
        } catch (SQLException e) {
            exceptionHolder = toRecoverySQLException(entry, e);
        } finally {
            exceptionHolder = mergeSQLException(exceptionHolder, closeRecoveryResource(recoveryResource));
        }
        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
    }

    private SQLException closeRecoveryResource(XaRecoveryResource recoveryResource) {
        if (recoveryResource == null) {
            return null;
        }
        try {
            recoveryResource.close();
            return null;
        } catch (SQLException e) {
            return e;
        }
    }

    private SQLException toRecoverySQLException(TransactionLogEntry entry, Exception e) {
        if (e instanceof SQLException && e.getMessage() != null &&
                e.getMessage().contains("MySplitter XA recovery failed for resource ")) {
            return (SQLException) e;
        }
        return new SQLException("MySplitter XA recovery failed for resource " + entry.getResourceId() +
                ", branch " + entry.getBranchId() + ".", e);
    }

    private Xid[] recoverXids(XAResource xaResource) throws XAException {
        List<Xid> xids = new ArrayList<Xid>();
        Xid[] recoveredXids = xaResource.recover(XAResource.TMSTARTRSCAN);
        addRecoveredXids(xids, recoveredXids);
        while (recoveredXids != null && recoveredXids.length > 0) {
            recoveredXids = xaResource.recover(XAResource.TMNOFLAGS);
            addRecoveredXids(xids, recoveredXids);
        }
        xaResource.recover(XAResource.TMENDRSCAN);
        return xids.toArray(new Xid[xids.size()]);
    }

    private void addRecoveredXids(List<Xid> target, Xid[] recoveredXids) {
        if (recoveredXids == null || recoveredXids.length == 0) {
            return;
        }
        for (Xid recoveredXid : recoveredXids) {
            target.add(recoveredXid);
        }
    }

    private void updateLog(TransactionLogEntry source, TransactionStatus status) throws SQLException {
        TransactionLogEntry entry = new TransactionLogEntry();
        entry.setGlobalTransactionId(source.getGlobalTransactionId());
        entry.setResourceId(source.getResourceId());
        entry.setBranchId(source.getBranchId());
        entry.setDecision(source.getDecision());
        entry.setStatus(status);
        transactionLogStore.update(entry);
    }

    private boolean containsXid(Xid[] recoveredXids, Xid expectedXid) {
        if (recoveredXids == null || recoveredXids.length == 0) {
            return false;
        }
        for (Xid recoveredXid : recoveredXids) {
            if (xidEquals(recoveredXid, expectedXid)) {
                return true;
            }
        }
        return false;
    }

    private boolean xidEquals(Xid first, Xid second) {
        if (first == null || second == null) {
            return false;
        }
        return first.getFormatId() == second.getFormatId() &&
                Arrays.equals(first.getGlobalTransactionId(), second.getGlobalTransactionId()) &&
                Arrays.equals(first.getBranchQualifier(), second.getBranchQualifier());
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
