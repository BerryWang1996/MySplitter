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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryTransactionLogStore implements TransactionLogStore {

    private final Map<String, TransactionLogEntry> entries =
            new LinkedHashMap<String, TransactionLogEntry>();

    @Override
    public synchronized void append(TransactionLogEntry entry) throws SQLException {
        TransactionLogEntry copiedEntry = copy(entry);
        String key = keyOf(copiedEntry);
        if (entries.containsKey(key)) {
            throw new SQLException("MySplitter transaction log already contains branch " + key + ".");
        }
        entries.put(key, copiedEntry);
    }

    @Override
    public synchronized void update(TransactionLogEntry entry) throws SQLException {
        TransactionLogEntry copiedEntry = copy(entry);
        String key = keyOf(copiedEntry);
        if (!entries.containsKey(key)) {
            throw new SQLException("MySplitter transaction log does not contain branch " + key + ".");
        }
        entries.put(key, copiedEntry);
    }

    @Override
    public synchronized List<TransactionLogEntry> findRecoverable() throws SQLException {
        List<TransactionLogEntry> recoverableEntries = new ArrayList<TransactionLogEntry>();
        for (TransactionLogEntry entry : entries.values()) {
            if (TransactionStatus.PREPARED.equals(entry.getStatus()) ||
                    TransactionStatus.FAILED.equals(entry.getStatus())) {
                recoverableEntries.add(copy(entry));
            }
        }
        return recoverableEntries;
    }

    public synchronized List<TransactionLogEntry> snapshot() {
        List<TransactionLogEntry> snapshot = new ArrayList<TransactionLogEntry>();
        for (TransactionLogEntry entry : entries.values()) {
            snapshot.add(copy(entry));
        }
        return snapshot;
    }

    private TransactionLogEntry copy(TransactionLogEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("MySplitter transaction log entry is null.");
        }
        TransactionLogEntry copiedEntry = new TransactionLogEntry();
        copiedEntry.setGlobalTransactionId(entry.getGlobalTransactionId());
        copiedEntry.setBranchId(entry.getBranchId());
        copiedEntry.setResourceId(entry.getResourceId());
        copiedEntry.setStatus(entry.getStatus());
        return copiedEntry;
    }

    private String keyOf(TransactionLogEntry entry) {
        return entry.getGlobalTransactionId() + ":" + entry.getResourceId() + ":" + entry.getBranchId();
    }
}
