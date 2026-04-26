package com.mysplitter.transaction;

import org.junit.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EmbeddedTransactionCoordinatorTest {

    @Test
    public void shouldPrepareAndCommitAllEnlistedBranches() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        EmbeddedTransactionCoordinator coordinator = new EmbeddedTransactionCoordinator(logStore);
        String globalTransactionId = coordinator.begin();
        RecordingBranch firstBranch = new RecordingBranch(globalTransactionId, "branch-1", "resource-1");
        RecordingBranch secondBranch = new RecordingBranch(globalTransactionId, "branch-2", "resource-2");

        coordinator.enlist(firstBranch);
        coordinator.enlist(secondBranch);
        coordinator.commit(globalTransactionId);

        assertEquals(1, firstBranch.prepares);
        assertEquals(1, secondBranch.prepares);
        assertEquals(1, firstBranch.commits);
        assertEquals(1, secondBranch.commits);
        assertEquals(0, firstBranch.rollbacks);
        assertEquals(0, secondBranch.rollbacks);
        assertStatuses(logStore.snapshot(), TransactionStatus.COMMITTED, TransactionStatus.COMMITTED);
    }

    @Test
    public void shouldRollbackBranchesWhenPrepareFails() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        EmbeddedTransactionCoordinator coordinator = new EmbeddedTransactionCoordinator(logStore);
        String globalTransactionId = coordinator.begin();
        RecordingBranch firstBranch = new RecordingBranch(globalTransactionId, "branch-1", "resource-1");
        RecordingBranch secondBranch = new RecordingBranch(globalTransactionId, "branch-2", "resource-2");
        secondBranch.prepareException = new SQLException("prepare failed");

        coordinator.enlist(firstBranch);
        coordinator.enlist(secondBranch);

        try {
            coordinator.commit(globalTransactionId);
            fail("Expected prepare failure to fail global commit.");
        } catch (SQLException e) {
            assertEquals("prepare failed", e.getMessage());
        }
        assertEquals(1, firstBranch.prepares);
        assertEquals(1, secondBranch.prepares);
        assertEquals(0, firstBranch.commits);
        assertEquals(0, secondBranch.commits);
        assertEquals(1, firstBranch.rollbacks);
        assertEquals(1, secondBranch.rollbacks);
        assertStatuses(logStore.snapshot(), TransactionStatus.ROLLED_BACK, TransactionStatus.ROLLED_BACK);
        assertEquals(0, logStore.findRecoverable().size());
    }

    @Test
    public void shouldKeepFailedCommitBranchRecoverable() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        EmbeddedTransactionCoordinator coordinator = new EmbeddedTransactionCoordinator(logStore);
        String globalTransactionId = coordinator.begin();
        RecordingBranch firstBranch = new RecordingBranch(globalTransactionId, "branch-1", "resource-1");
        RecordingBranch secondBranch = new RecordingBranch(globalTransactionId, "branch-2", "resource-2");
        secondBranch.commitException = new SQLException("commit failed");

        coordinator.enlist(firstBranch);
        coordinator.enlist(secondBranch);

        try {
            coordinator.commit(globalTransactionId);
            fail("Expected commit failure to fail global commit.");
        } catch (SQLException e) {
            assertEquals("commit failed", e.getMessage());
        }
        assertStatuses(logStore.snapshot(), TransactionStatus.COMMITTED, TransactionStatus.FAILED);
        List<TransactionLogEntry> recoverable = logStore.findRecoverable();
        assertEquals(1, recoverable.size());
        assertEquals("branch-2", recoverable.get(0).getBranchId());
    }

    @Test
    public void shouldRollbackAllBranchesInReverseOrder() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        EmbeddedTransactionCoordinator coordinator = new EmbeddedTransactionCoordinator(logStore);
        String globalTransactionId = coordinator.begin();
        RecordingBranch firstBranch = new RecordingBranch(globalTransactionId, "branch-1", "resource-1");
        RecordingBranch secondBranch = new RecordingBranch(globalTransactionId, "branch-2", "resource-2");

        coordinator.enlist(firstBranch);
        coordinator.enlist(secondBranch);
        coordinator.rollback(globalTransactionId);

        assertEquals(1, firstBranch.rollbacks);
        assertEquals(1, secondBranch.rollbacks);
        assertTrue("Second branch should rollback before first branch.",
                secondBranch.rollbackOrder < firstBranch.rollbackOrder);
        assertStatuses(logStore.snapshot(), TransactionStatus.ROLLED_BACK, TransactionStatus.ROLLED_BACK);
    }

    @Test
    public void shouldRejectDuplicateBranchEnlistment() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        EmbeddedTransactionCoordinator coordinator = new EmbeddedTransactionCoordinator(logStore);
        String globalTransactionId = coordinator.begin();
        RecordingBranch branch = new RecordingBranch(globalTransactionId, "branch-1", "resource-1");

        coordinator.enlist(branch);

        try {
            coordinator.enlist(branch);
            fail("Expected duplicate branch enlistment to be rejected.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("already enlisted"));
        }
    }

    private void assertStatuses(List<TransactionLogEntry> entries, TransactionStatus first, TransactionStatus second) {
        assertEquals(2, entries.size());
        assertEquals(first, entries.get(0).getStatus());
        assertEquals(second, entries.get(1).getStatus());
    }

    private static final class RecordingBranch implements BranchTransaction {

        private static int rollbackSequence;

        private final String globalTransactionId;

        private final String branchId;

        private final String resourceId;

        private int prepares;

        private int commits;

        private int rollbacks;

        private int rollbackOrder;

        private SQLException prepareException;

        private SQLException commitException;

        private RecordingBranch(String globalTransactionId, String branchId, String resourceId) {
            this.globalTransactionId = globalTransactionId;
            this.branchId = branchId;
            this.resourceId = resourceId;
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

        @Override
        public void prepare() throws SQLException {
            prepares++;
            if (prepareException != null) {
                throw prepareException;
            }
        }

        @Override
        public void commit() throws SQLException {
            commits++;
            if (commitException != null) {
                throw commitException;
            }
        }

        @Override
        public void rollback() throws SQLException {
            rollbacks++;
            rollbackOrder = ++rollbackSequence;
        }
    }
}
