package com.mysplitter.transaction;

import org.junit.Test;

import java.sql.SQLException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class XaBranchTransactionTest {

    @Test
    public void shouldDriveXaResourceLifecycle() throws Exception {
        RecordingXaResource xaResource = new RecordingXaResource(XAResource.XA_OK);
        XaBranchTransaction branch = new XaBranchTransaction("global-1", "branch-1", "resource-1", xaResource);

        branch.start(XAResource.TMNOFLAGS);
        branch.end(XAResource.TMSUCCESS);
        assertEquals(false, branch.prepare());
        branch.commit();

        assertEquals(1, xaResource.starts);
        assertEquals(1, xaResource.ends);
        assertEquals(1, xaResource.prepares);
        assertEquals(1, xaResource.commits);
        assertEquals(0, xaResource.rollbacks);
        assertEquals(false, xaResource.lastCommitOnePhase);
    }

    @Test
    public void shouldSkipCommitAndRollbackForReadOnlyBranch() throws Exception {
        RecordingXaResource xaResource = new RecordingXaResource(XAResource.XA_RDONLY);
        XaBranchTransaction branch = new XaBranchTransaction("global-1", "branch-1", "resource-1", xaResource);

        assertEquals(true, branch.prepare());
        branch.commit();
        branch.rollback();

        assertEquals(1, xaResource.prepares);
        assertEquals(0, xaResource.commits);
        assertEquals(0, xaResource.rollbacks);
    }

    @Test
    public void shouldWrapXaExceptionAsSqlException() throws Exception {
        RecordingXaResource xaResource = new RecordingXaResource(XAResource.XA_OK);
        xaResource.prepareException = new XAException(XAException.XAER_RMERR);
        XaBranchTransaction branch = new XaBranchTransaction("global-1", "branch-1", "resource-1", xaResource);

        try {
            branch.prepare();
            fail("Expected XA prepare failure to be wrapped.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("prepare failed"));
            assertTrue(e.getCause() instanceof XAException);
        }
    }

    @Test
    public void shouldBuildDefensiveXidCopies() {
        MySplitterXid xid = new MySplitterXid("global-1", "branch-1");

        byte[] global = xid.getGlobalTransactionId();
        byte[] branch = xid.getBranchQualifier();
        global[0] = 'x';
        branch[0] = 'y';

        assertArrayEquals("global-1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                xid.getGlobalTransactionId());
        assertArrayEquals("branch-1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                xid.getBranchQualifier());
    }

    private static final class RecordingXaResource implements XAResource {

        private final int prepareResult;

        private int starts;

        private int ends;

        private int prepares;

        private int commits;

        private int rollbacks;

        private boolean lastCommitOnePhase;

        private XAException prepareException;

        private RecordingXaResource(int prepareResult) {
            this.prepareResult = prepareResult;
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
            starts++;
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
            ends++;
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            prepares++;
            if (prepareException != null) {
                throw prepareException;
            }
            return prepareResult;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            commits++;
            lastCommitOnePhase = onePhase;
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            rollbacks++;
        }

        @Override
        public void forget(Xid xid) throws XAException {
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            return new Xid[0];
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
