package com.mysplitter.transaction;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileTransactionLogStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldAppendUpdatesAndReloadLatestBranchState() throws Exception {
        File logFile = temporaryFolder.newFile("xa-transaction.log");
        FileTransactionLogStore logStore = new FileTransactionLogStore(logFile);
        TransactionLogEntry branch = entry("global-1", "resource-1", "branch-1", TransactionStatus.ACTIVE);

        logStore.append(branch);
        branch.setStatus(TransactionStatus.PREPARED);
        logStore.update(branch);
        logStore.decide("global-1", TransactionDecision.COMMIT);

        FileTransactionLogStore reloadedLogStore = new FileTransactionLogStore(logFile);
        List<TransactionLogEntry> snapshot = reloadedLogStore.snapshot();

        assertEquals(1, snapshot.size());
        assertEquals("global-1", snapshot.get(0).getGlobalTransactionId());
        assertEquals("resource-1", snapshot.get(0).getResourceId());
        assertEquals("branch-1", snapshot.get(0).getBranchId());
        assertEquals(TransactionStatus.PREPARED, snapshot.get(0).getStatus());
        assertEquals(TransactionDecision.COMMIT, snapshot.get(0).getDecision());
    }

    @Test
    public void shouldKeepPreparedAndFailedEntriesRecoverableAcrossReloads() throws Exception {
        File logFile = temporaryFolder.newFile("xa-transaction.log");
        FileTransactionLogStore logStore = new FileTransactionLogStore(logFile);
        logStore.append(entry("global-1", "resource-1", "branch-1", TransactionStatus.PREPARED));
        logStore.append(entry("global-1", "resource-2", "branch-2", TransactionStatus.FAILED));
        logStore.append(entry("global-1", "resource-3", "branch-3", TransactionStatus.COMMITTED));

        FileTransactionLogStore reloadedLogStore = new FileTransactionLogStore(logFile);
        List<TransactionLogEntry> recoverableEntries = reloadedLogStore.findRecoverable();

        assertEquals(2, recoverableEntries.size());
        assertEquals("branch-1", recoverableEntries.get(0).getBranchId());
        assertEquals(TransactionStatus.PREPARED, recoverableEntries.get(0).getStatus());
        assertEquals("branch-2", recoverableEntries.get(1).getBranchId());
        assertEquals(TransactionStatus.FAILED, recoverableEntries.get(1).getStatus());
    }

    @Test
    public void shouldRejectDuplicateAppendAndMissingUpdate() throws Exception {
        File logFile = temporaryFolder.newFile("xa-transaction.log");
        FileTransactionLogStore logStore = new FileTransactionLogStore(logFile);
        TransactionLogEntry branch = entry("global-1", "resource-1", "branch-1", TransactionStatus.ACTIVE);

        logStore.append(branch);
        try {
            logStore.append(branch);
            fail("Expected duplicate append to be rejected.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("already contains branch"));
        }

        try {
            logStore.update(entry("global-2", "resource-1", "branch-1", TransactionStatus.ACTIVE));
            fail("Expected missing branch update to be rejected.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("does not contain branch"));
        }
    }

    @Test
    public void shouldRejectCorruptedLogRecordOnReload() throws Exception {
        File logFile = temporaryFolder.newFile("xa-transaction.log");
        FileOutputStream outputStream = new FileOutputStream(logFile);
        try {
            outputStream.write("not-a-valid-record\n".getBytes(StandardCharsets.UTF_8));
        } finally {
            outputStream.close();
        }

        try {
            new FileTransactionLogStore(logFile);
            fail("Expected corrupted transaction log to be rejected.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("invalid record"));
        }
    }

    private TransactionLogEntry entry(String globalTransactionId,
                                      String resourceId,
                                      String branchId,
                                      TransactionStatus status) {
        TransactionLogEntry entry = new TransactionLogEntry();
        entry.setGlobalTransactionId(globalTransactionId);
        entry.setResourceId(resourceId);
        entry.setBranchId(branchId);
        entry.setStatus(status);
        return entry;
    }
}
