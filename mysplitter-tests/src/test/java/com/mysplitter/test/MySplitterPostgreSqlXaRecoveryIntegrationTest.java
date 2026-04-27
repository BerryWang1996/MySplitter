package com.mysplitter.test;

import com.mysplitter.transaction.FileTransactionLogStore;
import com.mysplitter.transaction.MySplitterXid;
import com.mysplitter.transaction.TransactionDecision;
import com.mysplitter.transaction.TransactionLogEntry;
import com.mysplitter.transaction.TransactionStatus;
import com.mysplitter.transaction.XaDataSourceAdapter;
import com.mysplitter.transaction.XaRecoveryExecutor;
import com.mysplitter.transaction.XaRecoveryReport;
import com.mysplitter.transaction.XaResourceRegistry;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.postgresql.xa.PGXADataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MySplitterPostgreSqlXaRecoveryIntegrationTest {

    private static PostgreSQLContainer<?> POSTGRESQL_CONTAINER;

    @BeforeClass
    public static void setUpContainer() {
        Assume.assumeTrue(isDockerAvailable());
        POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("postgres:14.13")
                .withDatabaseName("mysplitter_xa")
                .withUsername("test")
                .withPassword("test")
                .withCommand("postgres", "-c", "max_prepared_transactions=10");
        POSTGRESQL_CONTAINER.start();
    }

    @AfterClass
    public static void tearDownContainer() {
        if (POSTGRESQL_CONTAINER != null) {
            POSTGRESQL_CONTAINER.stop();
        }
    }

    @Test
    public void shouldCommitPreparedPostgreSqlXaBranchDuringFileLogRecovery() throws Exception {
        String tableName = "xa_recovery_commit_" + uniqueSuffix();
        prepareTable(tableName);
        String globalTransactionId = "pg_global_commit_" + uniqueSuffix();
        String branchId = "pg_branch_commit";
        String resourceId = "postgresql-writer";
        preparePostgreSqlXaBranch(tableName, 1, "alpha", globalTransactionId, branchId);
        FileTransactionLogStore logStore = preparedFileLog(globalTransactionId, resourceId, branchId,
                TransactionDecision.COMMIT);

        XaRecoveryReport report = recoverPostgreSqlXaBranch(logStore, resourceId);

        assertEquals(1, report.getRecoveredBranchCount());
        assertEquals(0, report.getUnresolvedBranchCount());
        assertEquals("alpha", queryName(tableName, 1));
        assertEquals(0, logStore.findRecoverable().size());
    }

    @Test
    public void shouldRollbackPreparedPostgreSqlXaBranchDuringFileLogRecovery() throws Exception {
        String tableName = "xa_recovery_rollback_" + uniqueSuffix();
        prepareTable(tableName);
        String globalTransactionId = "pg_global_rollback_" + uniqueSuffix();
        String branchId = "pg_branch_rollback";
        String resourceId = "postgresql-writer";
        preparePostgreSqlXaBranch(tableName, 1, "alpha", globalTransactionId, branchId);
        FileTransactionLogStore logStore = preparedFileLog(globalTransactionId, resourceId, branchId,
                TransactionDecision.ROLLBACK);

        XaRecoveryReport report = recoverPostgreSqlXaBranch(logStore, resourceId);

        assertEquals(1, report.getRecoveredBranchCount());
        assertEquals(0, report.getUnresolvedBranchCount());
        assertEquals(0, queryRowCount(tableName));
        assertEquals(0, logStore.findRecoverable().size());
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable throwable) {
            return false;
        }
    }

    private void preparePostgreSqlXaBranch(String tableName,
                                           int id,
                                           String name,
                                           String globalTransactionId,
                                           String branchId) throws Exception {
        XAConnection xaConnection = null;
        Connection connection = null;
        Statement statement = null;
        try {
            xaConnection = xaDataSource().getXAConnection();
            connection = xaConnection.getConnection();
            XAResource xaResource = xaConnection.getXAResource();
            Xid xid = new MySplitterXid(globalTransactionId, branchId);
            xaResource.start(xid, XAResource.TMNOFLAGS);
            statement = connection.createStatement();
            statement.executeUpdate("INSERT INTO " + tableName + "(id, name) VALUES(" + id + ", '" + name + "')");
            xaResource.end(xid, XAResource.TMSUCCESS);
            assertEquals(XAResource.XA_OK, xaResource.prepare(xid));
        } finally {
            closeQuietly(statement);
            closeQuietly(connection);
            closeQuietly(xaConnection);
        }
    }

    private FileTransactionLogStore preparedFileLog(String globalTransactionId,
                                                    String resourceId,
                                                    String branchId,
                                                    TransactionDecision decision) throws Exception {
        File logFile = File.createTempFile("mysplitter-postgresql-xa-recovery-", ".log");
        logFile.deleteOnExit();
        FileTransactionLogStore logStore = new FileTransactionLogStore(logFile);
        TransactionLogEntry entry = new TransactionLogEntry();
        entry.setGlobalTransactionId(globalTransactionId);
        entry.setResourceId(resourceId);
        entry.setBranchId(branchId);
        entry.setStatus(TransactionStatus.PREPARED);
        entry.setDecision(TransactionDecision.UNKNOWN);
        logStore.append(entry);
        logStore.decide(globalTransactionId, decision);
        return new FileTransactionLogStore(logFile);
    }

    private XaRecoveryReport recoverPostgreSqlXaBranch(FileTransactionLogStore logStore, String resourceId)
            throws Exception {
        XaResourceRegistry registry = new XaResourceRegistry();
        registry.register(new XaDataSourceAdapter(resourceId, xaDataSource()));
        XaRecoveryExecutor recoveryExecutor = new XaRecoveryExecutor(logStore, registry);
        return recoveryExecutor.recover();
    }

    private PGXADataSource xaDataSource() {
        PGXADataSource dataSource = new PGXADataSource();
        dataSource.setServerNames(new String[]{POSTGRESQL_CONTAINER.getHost()});
        dataSource.setPortNumbers(new int[]{POSTGRESQL_CONTAINER.getFirstMappedPort()});
        dataSource.setDatabaseName(POSTGRESQL_CONTAINER.getDatabaseName());
        dataSource.setUser(POSTGRESQL_CONTAINER.getUsername());
        dataSource.setPassword(POSTGRESQL_CONTAINER.getPassword());
        return dataSource;
    }

    private void prepareTable(String tableName) throws Exception {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = DriverManager.getConnection(POSTGRESQL_CONTAINER.getJdbcUrl(),
                    POSTGRESQL_CONTAINER.getUsername(), POSTGRESQL_CONTAINER.getPassword());
            statement = connection.createStatement();
            statement.execute("CREATE TABLE " + tableName + "(id BIGINT PRIMARY KEY, name VARCHAR(64))");
        } finally {
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    private String queryName(String tableName, int id) throws Exception {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = DriverManager.getConnection(POSTGRESQL_CONTAINER.getJdbcUrl(),
                    POSTGRESQL_CONTAINER.getUsername(), POSTGRESQL_CONTAINER.getPassword());
            statement = connection.createStatement();
            resultSet = statement.executeQuery("SELECT name FROM " + tableName + " WHERE id = " + id);
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        } finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    private int queryRowCount(String tableName) throws Exception {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = DriverManager.getConnection(POSTGRESQL_CONTAINER.getJdbcUrl(),
                    POSTGRESQL_CONTAINER.getUsername(), POSTGRESQL_CONTAINER.getPassword());
            statement = connection.createStatement();
            resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName);
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        } finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    private String uniqueSuffix() {
        return Long.toHexString(System.nanoTime());
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            // Ignore cleanup failures in tests.
        }
    }

    private void closeQuietly(XAConnection closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            // Ignore cleanup failures in tests.
        }
    }
}
