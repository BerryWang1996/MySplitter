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

import com.mysplitter.util.StringUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class EmbeddedTransactionCoordinator implements TransactionCoordinator {

    private final AtomicLong transactionSequence = new AtomicLong();

    private final Map<String, GlobalTransaction> globalTransactions =
            new ConcurrentHashMap<String, GlobalTransaction>();

    private final TransactionLogStore transactionLogStore;

    public EmbeddedTransactionCoordinator(TransactionLogStore transactionLogStore) {
        if (transactionLogStore == null) {
            throw new IllegalArgumentException("MySplitter transactionLogStore is null.");
        }
        this.transactionLogStore = transactionLogStore;
    }

    @Override
    public String begin() throws SQLException {
        String globalTransactionId = "mysplitter-xa-" + transactionSequence.incrementAndGet();
        globalTransactions.put(globalTransactionId, new GlobalTransaction(globalTransactionId));
        return globalTransactionId;
    }

    @Override
    public void enlist(BranchTransaction branchTransaction) throws SQLException {
        if (branchTransaction == null) {
            throw new IllegalArgumentException("MySplitter branchTransaction is null.");
        }
        String globalTransactionId = branchTransaction.getGlobalTransactionId();
        if (StringUtil.isBlank(globalTransactionId)) {
            throw new IllegalArgumentException("MySplitter branch globalTransactionId is empty.");
        }
        GlobalTransaction globalTransaction = globalTransactions.get(globalTransactionId);
        if (globalTransaction == null) {
            throw new SQLException("MySplitter global transaction " + globalTransactionId + " does not exist.");
        }
        globalTransaction.enlist(branchTransaction);
        transactionLogStore.append(toLogEntry(branchTransaction, TransactionStatus.ACTIVE));
    }

    @Override
    public void commit(String globalTransactionId) throws SQLException {
        GlobalTransaction globalTransaction = requireGlobalTransaction(globalTransactionId);
        SQLException prepareException = prepareBranches(globalTransaction);
        if (prepareException != null) {
            SQLException rollbackException = rollbackBranches(globalTransaction, TransactionStatus.ROLLED_BACK);
            globalTransaction.status = TransactionStatus.FAILED;
            if (rollbackException != null) {
                prepareException.addSuppressed(rollbackException);
            }
            throw prepareException;
        }

        SQLException commitException = commitBranches(globalTransaction);
        if (commitException != null) {
            globalTransaction.status = TransactionStatus.FAILED;
            throw commitException;
        }
        globalTransaction.status = TransactionStatus.COMMITTED;
        globalTransactions.remove(globalTransactionId);
    }

    @Override
    public void rollback(String globalTransactionId) throws SQLException {
        GlobalTransaction globalTransaction = requireGlobalTransaction(globalTransactionId);
        SQLException rollbackException = rollbackBranches(globalTransaction, TransactionStatus.ROLLED_BACK);
        if (rollbackException != null) {
            globalTransaction.status = TransactionStatus.FAILED;
            throw rollbackException;
        }
        globalTransaction.status = TransactionStatus.ROLLED_BACK;
        globalTransactions.remove(globalTransactionId);
    }

    @Override
    public void recover() throws SQLException {
        // Durable recovery will be implemented when the persistent log store lands.
        transactionLogStore.findRecoverable();
    }

    private SQLException prepareBranches(GlobalTransaction globalTransaction) throws SQLException {
        SQLException exceptionHolder = null;
        for (BranchTransaction branchTransaction : globalTransaction.listBranches()) {
            try {
                branchTransaction.prepare();
                transactionLogStore.update(toLogEntry(branchTransaction, TransactionStatus.PREPARED));
            } catch (SQLException e) {
                transactionLogStore.update(toLogEntry(branchTransaction, TransactionStatus.FAILED));
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        return exceptionHolder;
    }

    private SQLException commitBranches(GlobalTransaction globalTransaction) throws SQLException {
        SQLException exceptionHolder = null;
        for (BranchTransaction branchTransaction : globalTransaction.listBranches()) {
            try {
                branchTransaction.commit();
                transactionLogStore.update(toLogEntry(branchTransaction, TransactionStatus.COMMITTED));
            } catch (SQLException e) {
                transactionLogStore.update(toLogEntry(branchTransaction, TransactionStatus.FAILED));
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        return exceptionHolder;
    }

    private SQLException rollbackBranches(GlobalTransaction globalTransaction, TransactionStatus successStatus)
            throws SQLException {
        SQLException exceptionHolder = null;
        List<BranchTransaction> branches = globalTransaction.listBranches();
        for (int i = branches.size() - 1; i >= 0; i--) {
            BranchTransaction branchTransaction = branches.get(i);
            try {
                branchTransaction.rollback();
                transactionLogStore.update(toLogEntry(branchTransaction, successStatus));
            } catch (SQLException e) {
                transactionLogStore.update(toLogEntry(branchTransaction, TransactionStatus.FAILED));
                exceptionHolder = mergeSQLException(exceptionHolder, e);
            }
        }
        return exceptionHolder;
    }

    private GlobalTransaction requireGlobalTransaction(String globalTransactionId) throws SQLException {
        if (StringUtil.isBlank(globalTransactionId)) {
            throw new IllegalArgumentException("MySplitter globalTransactionId is empty.");
        }
        GlobalTransaction globalTransaction = globalTransactions.get(globalTransactionId);
        if (globalTransaction == null) {
            throw new SQLException("MySplitter global transaction " + globalTransactionId + " does not exist.");
        }
        return globalTransaction;
    }

    private TransactionLogEntry toLogEntry(BranchTransaction branchTransaction, TransactionStatus status) {
        TransactionLogEntry entry = new TransactionLogEntry();
        entry.setGlobalTransactionId(branchTransaction.getGlobalTransactionId());
        entry.setBranchId(branchTransaction.getBranchId());
        entry.setResourceId(branchTransaction.getResourceId());
        entry.setStatus(status);
        return entry;
    }

    private SQLException mergeSQLException(SQLException current, SQLException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static final class GlobalTransaction {

        private final String globalTransactionId;

        private final Map<String, BranchTransaction> branchTransactions =
                new LinkedHashMap<String, BranchTransaction>();

        private TransactionStatus status = TransactionStatus.ACTIVE;

        private GlobalTransaction(String globalTransactionId) {
            this.globalTransactionId = globalTransactionId;
        }

        private synchronized void enlist(BranchTransaction branchTransaction) throws SQLException {
            if (!TransactionStatus.ACTIVE.equals(status)) {
                throw new SQLException("MySplitter global transaction " + globalTransactionId +
                        " cannot enlist branch when status is " + status + ".");
            }
            String branchKey = branchTransaction.getResourceId() + ":" + branchTransaction.getBranchId();
            if (branchTransactions.containsKey(branchKey)) {
                throw new SQLException("MySplitter global transaction " + globalTransactionId +
                        " already enlisted branch " + branchTransaction.getBranchId() +
                        " for resource " + branchTransaction.getResourceId() + ".");
            }
            branchTransactions.put(branchKey, branchTransaction);
        }

        private synchronized List<BranchTransaction> listBranches() {
            return new ArrayList<BranchTransaction>(branchTransactions.values());
        }
    }
}
