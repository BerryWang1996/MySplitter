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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FileTransactionLogStore implements TransactionLogStore {

    private static final String RECORD_VERSION = "v3";

    private static final String LEGACY_RECORD_VERSION = "v1";

    private static final String INLINE_DECISION_RECORD_VERSION = "v2";

    private static final String BRANCH_RECORD_TYPE = "branch";

    private static final String DECISION_RECORD_TYPE = "decision";

    private static final String FIELD_SEPARATOR = "\t";

    private final Map<String, TransactionLogEntry> entries =
            new LinkedHashMap<String, TransactionLogEntry>();

    private final Map<String, TransactionDecision> decisions =
            new LinkedHashMap<String, TransactionDecision>();

    private final File logFile;

    public FileTransactionLogStore(File logFile) throws SQLException {
        if (logFile == null) {
            throw new IllegalArgumentException("MySplitter transaction log file is null.");
        }
        this.logFile = logFile;
        load();
    }

    @Override
    public synchronized void append(TransactionLogEntry entry) throws SQLException {
        TransactionLogEntry copiedEntry = copy(entry);
        String key = keyOf(copiedEntry);
        if (entries.containsKey(key)) {
            throw new SQLException("MySplitter transaction log already contains branch " + key + ".");
        }
        appendRecord(copiedEntry);
        entries.put(key, copiedEntry);
    }

    @Override
    public synchronized void update(TransactionLogEntry entry) throws SQLException {
        TransactionLogEntry copiedEntry = copy(entry);
        String key = keyOf(copiedEntry);
        if (!entries.containsKey(key)) {
            throw new SQLException("MySplitter transaction log does not contain branch " + key + ".");
        }
        appendRecord(copiedEntry);
        entries.put(key, copiedEntry);
    }

    @Override
    public synchronized void decide(String globalTransactionId, TransactionDecision decision) throws SQLException {
        if (isBlank(globalTransactionId)) {
            throw new IllegalArgumentException("MySplitter transaction log globalTransactionId is empty.");
        }
        if (decision == null || TransactionDecision.UNKNOWN.equals(decision)) {
            throw new IllegalArgumentException("MySplitter transaction decision must be COMMIT or ROLLBACK.");
        }
        appendDecisionRecord(globalTransactionId, decision);
        decisions.put(globalTransactionId, decision);
    }

    @Override
    public synchronized List<TransactionLogEntry> findRecoverable() throws SQLException {
        List<TransactionLogEntry> recoverableEntries = new ArrayList<TransactionLogEntry>();
        for (TransactionLogEntry entry : entries.values()) {
            if (TransactionStatus.PREPARED.equals(entry.getStatus()) ||
                    TransactionStatus.FAILED.equals(entry.getStatus())) {
                recoverableEntries.add(copyWithDecision(entry));
            }
        }
        return recoverableEntries;
    }

    public synchronized List<TransactionLogEntry> snapshot() {
        List<TransactionLogEntry> snapshot = new ArrayList<TransactionLogEntry>();
        for (TransactionLogEntry entry : entries.values()) {
            snapshot.add(copyWithDecision(entry));
        }
        return snapshot;
    }

    public File getLogFile() {
        return logFile;
    }

    private void load() throws SQLException {
        entries.clear();
        decisions.clear();
        if (!logFile.exists()) {
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile),
                    StandardCharsets.UTF_8));
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().length() == 0) {
                    continue;
                }
                parseRecord(line, lineNumber);
            }
        } catch (IOException e) {
            throw new SQLException("MySplitter failed to read transaction log file " +
                    logFile.getAbsolutePath() + ".", e);
        } finally {
            closeQuietly(reader);
        }
    }

    private void appendDecisionRecord(String globalTransactionId, TransactionDecision decision) throws SQLException {
        appendLine(serializeDecisionRecord(globalTransactionId, decision));
    }

    private void appendRecord(TransactionLogEntry entry) throws SQLException {
        appendLine(serializeRecord(entry));
    }

    private void appendLine(String record) throws SQLException {
        File parentFile = logFile.getParentFile();
        if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs() && !parentFile.exists()) {
            throw new SQLException("MySplitter failed to create transaction log directory " +
                    parentFile.getAbsolutePath() + ".");
        }

        FileOutputStream outputStream = null;
        Writer writer = null;
        try {
            outputStream = new FileOutputStream(logFile, true);
            writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            writer.write(record);
            writer.write(System.lineSeparator());
            writer.flush();
            outputStream.getFD().sync();
        } catch (IOException e) {
            throw new SQLException("MySplitter failed to append transaction log file " +
                    logFile.getAbsolutePath() + ".", e);
        } finally {
            closeQuietly(writer);
            closeQuietly(outputStream);
        }
    }

    private String serializeRecord(TransactionLogEntry entry) {
        return RECORD_VERSION + FIELD_SEPARATOR +
                BRANCH_RECORD_TYPE + FIELD_SEPARATOR +
                encode(entry.getGlobalTransactionId()) + FIELD_SEPARATOR +
                encode(entry.getResourceId()) + FIELD_SEPARATOR +
                encode(entry.getBranchId()) + FIELD_SEPARATOR +
                entry.getStatus().name();
    }

    private String serializeDecisionRecord(String globalTransactionId, TransactionDecision decision) {
        return RECORD_VERSION + FIELD_SEPARATOR +
                DECISION_RECORD_TYPE + FIELD_SEPARATOR +
                encode(globalTransactionId) + FIELD_SEPARATOR +
                decision.name();
    }

    private void parseRecord(String line, int lineNumber) throws SQLException {
        String[] fields = line.split(FIELD_SEPARATOR, -1);
        if (isBranchRecord(fields)) {
            TransactionLogEntry entry = parseBranchRecord(fields, lineNumber);
            entries.put(keyOf(entry), entry);
            return;
        }
        if (isDecisionRecord(fields)) {
            decisions.put(decode(fields[2], lineNumber), parseDecision(fields[3], lineNumber));
            return;
        }
        throw new SQLException("MySplitter transaction log file " + logFile.getAbsolutePath() +
                " contains invalid record at line " + lineNumber + ".");
    }

    private TransactionLogEntry parseBranchRecord(String[] fields, int lineNumber) throws SQLException {
        TransactionLogEntry entry = new TransactionLogEntry();
        if (RECORD_VERSION.equals(fields[0])) {
            entry.setGlobalTransactionId(decode(fields[2], lineNumber));
            entry.setResourceId(decode(fields[3], lineNumber));
            entry.setBranchId(decode(fields[4], lineNumber));
            entry.setStatus(parseStatus(fields[5], lineNumber));
            entry.setDecision(TransactionDecision.UNKNOWN);
        } else if (INLINE_DECISION_RECORD_VERSION.equals(fields[0])) {
            entry.setGlobalTransactionId(decode(fields[1], lineNumber));
            entry.setResourceId(decode(fields[2], lineNumber));
            entry.setBranchId(decode(fields[3], lineNumber));
            entry.setStatus(parseStatus(fields[4], lineNumber));
            entry.setDecision(parseDecision(fields[5], lineNumber));
        } else if (LEGACY_RECORD_VERSION.equals(fields[0])) {
            entry.setGlobalTransactionId(decode(fields[1], lineNumber));
            entry.setResourceId(decode(fields[2], lineNumber));
            entry.setBranchId(decode(fields[3], lineNumber));
            entry.setStatus(parseStatus(fields[4], lineNumber));
            entry.setDecision(TransactionDecision.UNKNOWN);
        } else {
            throw new SQLException("MySplitter transaction log file " + logFile.getAbsolutePath() +
                    " contains invalid record at line " + lineNumber + ".");
        }
        try {
            validate(entry);
        } catch (IllegalArgumentException e) {
            throw new SQLException("MySplitter transaction log file " + logFile.getAbsolutePath() +
                    " contains invalid record at line " + lineNumber + ".", e);
        }
        return entry;
    }

    private TransactionStatus parseStatus(String value, int lineNumber) throws SQLException {
        try {
            return TransactionStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new SQLException("MySplitter transaction log file " + logFile.getAbsolutePath() +
                    " contains invalid status at line " + lineNumber + ".", e);
        }
    }

    private TransactionDecision parseDecision(String value, int lineNumber) throws SQLException {
        try {
            return TransactionDecision.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new SQLException("MySplitter transaction log file " + logFile.getAbsolutePath() +
                    " contains invalid decision at line " + lineNumber + ".", e);
        }
    }

    private TransactionLogEntry copy(TransactionLogEntry entry) {
        validate(entry);
        TransactionLogEntry copiedEntry = new TransactionLogEntry();
        copiedEntry.setGlobalTransactionId(entry.getGlobalTransactionId());
        copiedEntry.setBranchId(entry.getBranchId());
        copiedEntry.setResourceId(entry.getResourceId());
        copiedEntry.setStatus(entry.getStatus());
        copiedEntry.setDecision(entry.getDecision() == null ? TransactionDecision.UNKNOWN : entry.getDecision());
        return copiedEntry;
    }

    private TransactionLogEntry copyWithDecision(TransactionLogEntry entry) {
        TransactionLogEntry copiedEntry = copy(entry);
        TransactionDecision globalDecision = decisions.get(copiedEntry.getGlobalTransactionId());
        if (globalDecision != null && TransactionDecision.UNKNOWN.equals(copiedEntry.getDecision())) {
            copiedEntry.setDecision(globalDecision);
        }
        return copiedEntry;
    }

    private void validate(TransactionLogEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("MySplitter transaction log entry is null.");
        }
        if (isBlank(entry.getGlobalTransactionId())) {
            throw new IllegalArgumentException("MySplitter transaction log globalTransactionId is empty.");
        }
        if (isBlank(entry.getResourceId())) {
            throw new IllegalArgumentException("MySplitter transaction log resourceId is empty.");
        }
        if (isBlank(entry.getBranchId())) {
            throw new IllegalArgumentException("MySplitter transaction log branchId is empty.");
        }
        if (entry.getStatus() == null) {
            throw new IllegalArgumentException("MySplitter transaction log status is null.");
        }
        if (entry.getDecision() == null) {
            throw new IllegalArgumentException("MySplitter transaction log decision is null.");
        }
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value, int lineNumber) throws SQLException {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new SQLException("MySplitter transaction log file " + logFile.getAbsolutePath() +
                    " contains invalid encoded field at line " + lineNumber + ".", e);
        }
    }

    private String keyOf(TransactionLogEntry entry) {
        return entry.getGlobalTransactionId() + ":" + entry.getResourceId() + ":" + entry.getBranchId();
    }

    private boolean isBranchRecord(String[] fields) {
        if (fields.length == 6 && RECORD_VERSION.equals(fields[0]) && BRANCH_RECORD_TYPE.equals(fields[1])) {
            return true;
        }
        if (fields.length == 6 && INLINE_DECISION_RECORD_VERSION.equals(fields[0])) {
            return true;
        }
        return fields.length == 5 && LEGACY_RECORD_VERSION.equals(fields[0]);
    }

    private boolean isDecisionRecord(String[] fields) {
        return fields.length == 4 && RECORD_VERSION.equals(fields[0]) && DECISION_RECORD_TYPE.equals(fields[1]);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Preserve the original log-store failure.
        }
    }
}
