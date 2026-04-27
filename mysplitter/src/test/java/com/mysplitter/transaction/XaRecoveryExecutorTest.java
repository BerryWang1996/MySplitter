package com.mysplitter.transaction;

import org.junit.Test;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class XaRecoveryExecutorTest {

    @Test
    public void shouldCommitPreparedBranchWhenCommitDecisionIsDurable() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.PREPARED, TransactionDecision.COMMIT));
        RecordingXaDataSource xaDataSource = xaDataSource(new MySplitterXid("global-1", "branch-1"));
        XaRecoveryExecutor recoveryExecutor = newRecoveryExecutor(logStore, xaDataSource);

        XaRecoveryReport report = recoveryExecutor.recover();

        assertEquals(1, report.getRecoveredBranchCount());
        assertEquals(0, report.getUnresolvedBranchCount());
        assertEquals(1, xaDataSource.lastConnection.xaResource.commits);
        assertEquals(0, xaDataSource.lastConnection.xaResource.rollbacks);
        assertTrue(xaDataSource.lastConnection.closed);
        assertStatus(logStore.snapshot(), TransactionStatus.COMMITTED, TransactionDecision.COMMIT);
        assertEquals(0, logStore.findRecoverable().size());
    }

    @Test
    public void shouldRollbackPreparedBranchWhenRollbackDecisionIsDurable() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.FAILED, TransactionDecision.ROLLBACK));
        RecordingXaDataSource xaDataSource = xaDataSource(new MySplitterXid("global-1", "branch-1"));
        XaRecoveryExecutor recoveryExecutor = newRecoveryExecutor(logStore, xaDataSource);

        XaRecoveryReport report = recoveryExecutor.recover();

        assertEquals(1, report.getRecoveredBranchCount());
        assertEquals(0, report.getUnresolvedBranchCount());
        assertEquals(0, xaDataSource.lastConnection.xaResource.commits);
        assertEquals(1, xaDataSource.lastConnection.xaResource.rollbacks);
        assertTrue(xaDataSource.lastConnection.closed);
        assertStatus(logStore.snapshot(), TransactionStatus.ROLLED_BACK, TransactionDecision.ROLLBACK);
        assertEquals(0, logStore.findRecoverable().size());
    }

    @Test
    public void shouldLeaveUnknownDecisionBranchesRecoverable() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.PREPARED, TransactionDecision.UNKNOWN));
        XaRecoveryExecutor recoveryExecutor = new XaRecoveryExecutor(logStore, new XaResourceRegistry());

        XaRecoveryReport report = recoveryExecutor.recover();

        assertEquals(0, report.getRecoveredBranchCount());
        assertEquals(1, report.getUnresolvedBranchCount());
        assertStatus(logStore.snapshot(), TransactionStatus.PREPARED, TransactionDecision.UNKNOWN);
        assertEquals(1, logStore.findRecoverable().size());
    }

    @Test
    public void shouldIgnoreRecoveredXidsThatDoNotMatchLoggedBranch() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.PREPARED, TransactionDecision.COMMIT));
        RecordingXaDataSource xaDataSource = xaDataSource(new MySplitterXid("other-global", "other-branch"));
        XaRecoveryExecutor recoveryExecutor = newRecoveryExecutor(logStore, xaDataSource);

        XaRecoveryReport report = recoveryExecutor.recover();

        assertEquals(0, report.getRecoveredBranchCount());
        assertEquals(1, report.getUnresolvedBranchCount());
        assertEquals(0, xaDataSource.lastConnection.xaResource.commits);
        assertEquals(0, xaDataSource.lastConnection.xaResource.rollbacks);
        assertStatus(logStore.snapshot(), TransactionStatus.PREPARED, TransactionDecision.COMMIT);
    }

    @Test
    public void shouldFailClearlyWhenLoggedResourceIsNotRegistered() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.PREPARED, TransactionDecision.COMMIT));
        XaRecoveryExecutor recoveryExecutor = new XaRecoveryExecutor(logStore, new XaResourceRegistry());

        try {
            recoveryExecutor.recover();
            fail("Expected missing recovery resource to fail.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("resource-1"));
            assertTrue(e.getMessage().contains("branch-1"));
            assertTrue(e.getCause().getMessage().contains("not registered"));
        }
        assertStatus(logStore.snapshot(), TransactionStatus.PREPARED, TransactionDecision.COMMIT);
    }

    @Test
    public void shouldFailClearlyWhenRecoveryResourceCannotOpen() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.PREPARED, TransactionDecision.COMMIT));
        RecordingXaDataSource xaDataSource = xaDataSource(new MySplitterXid("global-1", "branch-1"));
        xaDataSource.openException = new SQLException("database unavailable");
        XaRecoveryExecutor recoveryExecutor = newRecoveryExecutor(logStore, xaDataSource);

        try {
            recoveryExecutor.recover();
            fail("Expected recovery resource open failure.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("resource-1"));
            assertTrue(e.getMessage().contains("branch-1"));
            assertTrue(e.getCause().getMessage().contains("database unavailable"));
        }

        assertStatus(logStore.snapshot(), TransactionStatus.PREPARED, TransactionDecision.COMMIT);
        assertEquals(1, logStore.findRecoverable().size());
    }

    @Test
    public void shouldKeepBranchRecoverableWhenRecoveryCommitFails() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.PREPARED, TransactionDecision.COMMIT));
        RecordingXaDataSource xaDataSource = xaDataSource(new MySplitterXid("global-1", "branch-1"));
        xaDataSource.commitException = new XAException(XAException.XAER_RMERR);
        XaRecoveryExecutor recoveryExecutor = newRecoveryExecutor(logStore, xaDataSource);

        try {
            recoveryExecutor.recover();
            fail("Expected recovery commit failure.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("resource-1"));
            assertTrue(e.getMessage().contains("branch-1"));
            assertTrue(e.getCause() instanceof XAException);
        }

        assertEquals(1, xaDataSource.lastConnection.xaResource.commits);
        assertEquals(0, xaDataSource.lastConnection.xaResource.rollbacks);
        assertTrue(xaDataSource.lastConnection.closed);
        assertStatus(logStore.snapshot(), TransactionStatus.PREPARED, TransactionDecision.COMMIT);
        assertEquals(1, logStore.findRecoverable().size());
    }

    @Test
    public void shouldKeepBranchRecoverableWhenRecoveryRollbackFails() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.FAILED, TransactionDecision.ROLLBACK));
        RecordingXaDataSource xaDataSource = xaDataSource(new MySplitterXid("global-1", "branch-1"));
        xaDataSource.rollbackException = new XAException(XAException.XAER_RMERR);
        XaRecoveryExecutor recoveryExecutor = newRecoveryExecutor(logStore, xaDataSource);

        try {
            recoveryExecutor.recover();
            fail("Expected recovery rollback failure.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("resource-1"));
            assertTrue(e.getMessage().contains("branch-1"));
            assertTrue(e.getCause() instanceof XAException);
        }

        assertEquals(0, xaDataSource.lastConnection.xaResource.commits);
        assertEquals(1, xaDataSource.lastConnection.xaResource.rollbacks);
        assertTrue(xaDataSource.lastConnection.closed);
        assertStatus(logStore.snapshot(), TransactionStatus.FAILED, TransactionDecision.ROLLBACK);
        assertEquals(1, logStore.findRecoverable().size());
    }

    @Test
    public void shouldCloseRecoveryResourceWhenRecoverScanFails() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.PREPARED, TransactionDecision.COMMIT));
        RecordingXaDataSource xaDataSource = xaDataSource(new MySplitterXid("global-1", "branch-1"));
        xaDataSource.recoverException = new XAException(XAException.XAER_RMERR);
        XaRecoveryExecutor recoveryExecutor = newRecoveryExecutor(logStore, xaDataSource);

        try {
            recoveryExecutor.recover();
            fail("Expected recover scan failure.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("resource-1"));
            assertTrue(e.getMessage().contains("branch-1"));
            assertTrue(e.getCause() instanceof XAException);
        }

        assertEquals(0, xaDataSource.lastConnection.xaResource.commits);
        assertEquals(0, xaDataSource.lastConnection.xaResource.rollbacks);
        assertTrue(xaDataSource.lastConnection.closed);
        assertStatus(logStore.snapshot(), TransactionStatus.PREPARED, TransactionDecision.COMMIT);
    }

    @Test
    public void shouldNotMaskRecoveryFailureWhenResourceCloseAlsoFails() throws Exception {
        InMemoryTransactionLogStore logStore = new InMemoryTransactionLogStore();
        logStore.append(entry("global-1", "resource-1", "branch-1",
                TransactionStatus.PREPARED, TransactionDecision.COMMIT));
        RecordingXaDataSource xaDataSource = xaDataSource(new MySplitterXid("global-1", "branch-1"));
        xaDataSource.recoverException = new XAException(XAException.XAER_RMERR);
        xaDataSource.closeException = new SQLException("close failed");
        XaRecoveryExecutor recoveryExecutor = newRecoveryExecutor(logStore, xaDataSource);

        try {
            recoveryExecutor.recover();
            fail("Expected recover scan failure.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("resource-1"));
            assertTrue(e.getMessage().contains("branch-1"));
            assertTrue(e.getCause() instanceof XAException);
            assertEquals(1, e.getSuppressed().length);
            assertTrue(e.getSuppressed()[0].getMessage().contains("close failed"));
        }

        assertTrue(xaDataSource.lastConnection.closed);
        assertStatus(logStore.snapshot(), TransactionStatus.PREPARED, TransactionDecision.COMMIT);
    }

    private XaRecoveryExecutor newRecoveryExecutor(InMemoryTransactionLogStore logStore,
                                                   RecordingXaDataSource xaDataSource) {
        XaResourceRegistry xaResourceRegistry = new XaResourceRegistry();
        xaResourceRegistry.register(new XaDataSourceAdapter("resource-1", xaDataSource));
        return new XaRecoveryExecutor(logStore, xaResourceRegistry);
    }

    private RecordingXaDataSource xaDataSource(Xid recoveredXid) {
        return new RecordingXaDataSource(new Xid[]{recoveredXid});
    }

    private void assertStatus(List<TransactionLogEntry> entries,
                              TransactionStatus status,
                              TransactionDecision decision) {
        assertEquals(1, entries.size());
        assertEquals(status, entries.get(0).getStatus());
        assertEquals(decision, entries.get(0).getDecision());
    }

    private TransactionLogEntry entry(String globalTransactionId,
                                      String resourceId,
                                      String branchId,
                                      TransactionStatus status,
                                      TransactionDecision decision) {
        TransactionLogEntry entry = new TransactionLogEntry();
        entry.setGlobalTransactionId(globalTransactionId);
        entry.setResourceId(resourceId);
        entry.setBranchId(branchId);
        entry.setStatus(status);
        entry.setDecision(decision);
        return entry;
    }

    private static final class RecordingXaDataSource implements XADataSource {

        private final Xid[] recoveredXids;

        private RecordingXaConnection lastConnection;

        private XAException commitException;

        private XAException rollbackException;

        private XAException recoverException;

        private SQLException openException;

        private SQLException closeException;

        private RecordingXaDataSource(Xid[] recoveredXids) {
            this.recoveredXids = recoveredXids;
        }

        @Override
        public XAConnection getXAConnection() throws SQLException {
            if (openException != null) {
                throw openException;
            }
            lastConnection = new RecordingXaConnection(recoveredXids,
                    commitException, rollbackException, recoverException, closeException);
            return lastConnection;
        }

        @Override
        public XAConnection getXAConnection(String user, String password) throws SQLException {
            return getXAConnection();
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    private static final class RecordingXaConnection implements XAConnection {

        private final RecordingXaResource xaResource;

        private boolean closed;

        private RecordingXaConnection(Xid[] recoveredXids,
                                      XAException commitException,
                                      XAException rollbackException,
                                      XAException recoverException,
                                      SQLException closeException) {
            this.xaResource = new RecordingXaResource(recoveredXids,
                    commitException, rollbackException, recoverException);
            this.closeException = closeException;
        }

        private final SQLException closeException;

        @Override
        public Connection getConnection() throws SQLException {
            return null;
        }

        @Override
        public XAResource getXAResource() throws SQLException {
            return xaResource;
        }

        @Override
        public void close() throws SQLException {
            closed = true;
            if (closeException != null) {
                throw closeException;
            }
        }

        @Override
        public void addConnectionEventListener(javax.sql.ConnectionEventListener listener) {
        }

        @Override
        public void removeConnectionEventListener(javax.sql.ConnectionEventListener listener) {
        }

        @Override
        public void addStatementEventListener(javax.sql.StatementEventListener listener) {
        }

        @Override
        public void removeStatementEventListener(javax.sql.StatementEventListener listener) {
        }
    }

    private static final class RecordingXaResource implements XAResource {

        private final Xid[] recoveredXids;

        private int commits;

        private int rollbacks;

        private final XAException commitException;

        private final XAException rollbackException;

        private final XAException recoverException;

        private RecordingXaResource(Xid[] recoveredXids,
                                    XAException commitException,
                                    XAException rollbackException,
                                    XAException recoverException) {
            this.recoveredXids = recoveredXids;
            this.commitException = commitException;
            this.rollbackException = rollbackException;
            this.recoverException = recoverException;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            commits++;
            assertSame(Boolean.FALSE, Boolean.valueOf(onePhase));
            if (commitException != null) {
                throw commitException;
            }
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            rollbacks++;
            if (rollbackException != null) {
                throw rollbackException;
            }
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            if (recoverException != null) {
                throw recoverException;
            }
            if (flag == XAResource.TMSTARTRSCAN) {
                return recoveredXids;
            }
            assertTrue(flag == XAResource.TMNOFLAGS || flag == XAResource.TMENDRSCAN);
            return new Xid[0];
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            return XAResource.XA_OK;
        }

        @Override
        public void forget(Xid xid) throws XAException {
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            return xaResource == this;
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return 0;
        }

        @Override
        public boolean setTransactionTimeout(int seconds) throws XAException {
            return false;
        }
    }
}
