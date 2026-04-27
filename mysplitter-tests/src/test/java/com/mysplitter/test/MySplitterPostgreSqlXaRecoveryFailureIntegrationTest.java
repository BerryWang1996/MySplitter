package com.mysplitter.test;

import com.mysplitter.transaction.FileTransactionLogStore;
import com.mysplitter.transaction.MySplitterXid;
import com.mysplitter.transaction.TransactionDecision;
import com.mysplitter.transaction.TransactionLogEntry;
import com.mysplitter.transaction.TransactionStatus;
import com.mysplitter.transaction.XaDataSourceAdapter;
import com.mysplitter.transaction.XaRecoveryExecutor;
import com.mysplitter.transaction.XaResourceRegistry;
import org.junit.Assume;
import org.junit.Test;
import org.postgresql.xa.PGXADataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MySplitterPostgreSqlXaRecoveryFailureIntegrationTest {

    @Test
    public void shouldKeepPreparedBranchRecoverableWhenPostgreSqlIsUnavailableDuringRecovery() throws Exception {
        Assume.assumeTrue(isDockerAvailable());
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:14.13")
                .withDatabaseName("mysplitter_xa")
                .withUsername("test")
                .withPassword("test")
                .withCommand("postgres", "-c", "max_prepared_transactions=10");
        container.start();

        String tableName = "xa_recovery_unavailable_" + uniqueSuffix();
        String globalTransactionId = "pg_global_unavailable_" + uniqueSuffix();
        String branchId = "pg_branch_unavailable";
        String resourceId = "postgresql-writer";
        PGXADataSource dataSource = xaDataSource(container);
        FileTransactionLogStore logStore = null;
        try {
            prepareTable(container, tableName);
            preparePostgreSqlXaBranch(dataSource, tableName, 1, "alpha", globalTransactionId, branchId);
            logStore = preparedFileLog(globalTransactionId, resourceId, branchId, TransactionDecision.COMMIT);

            container.stop();
            try {
                recoverPostgreSqlXaBranch(logStore, resourceId, dataSource);
                fail("Expected recovery to fail while PostgreSQL is unavailable.");
            } catch (SQLException expected) {
                // The important guarantee is that recovery failure does not mark the branch complete.
            }

            List<TransactionLogEntry> recoverable = new FileTransactionLogStore(logStore.getLogFile())
                    .findRecoverable();
            assertEquals(1, recoverable.size());
            assertEquals(globalTransactionId, recoverable.get(0).getGlobalTransactionId());
            assertEquals(resourceId, recoverable.get(0).getResourceId());
            assertEquals(branchId, recoverable.get(0).getBranchId());
            assertEquals(TransactionStatus.PREPARED, recoverable.get(0).getStatus());
            assertEquals(TransactionDecision.COMMIT, recoverable.get(0).getDecision());
        } finally {
            container.stop();
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable throwable) {
            return false;
        }
    }

    private void preparePostgreSqlXaBranch(PGXADataSource dataSource,
                                           String tableName,
                                           int id,
                                           String name,
                                           String globalTransactionId,
                                           String branchId) throws Exception {
        XAConnection xaConnection = null;
        Connection connection = null;
        Statement statement = null;
        try {
            xaConnection = dataSource.getXAConnection();
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
        File logFile = File.createTempFile("mysplitter-postgresql-xa-unavailable-", ".log");
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

    private void recoverPostgreSqlXaBranch(FileTransactionLogStore logStore,
                                           String resourceId,
                                           PGXADataSource dataSource) throws Exception {
        XaResourceRegistry registry = new XaResourceRegistry();
        registry.register(new XaDataSourceAdapter(resourceId, dataSource));
        XaRecoveryExecutor recoveryExecutor = new XaRecoveryExecutor(logStore, registry);
        recoveryExecutor.recover();
    }

    private PGXADataSource xaDataSource(PostgreSQLContainer<?> container) {
        PGXADataSource dataSource = new PGXADataSource();
        dataSource.setServerNames(new String[]{container.getHost()});
        dataSource.setPortNumbers(new int[]{container.getFirstMappedPort()});
        dataSource.setDatabaseName(container.getDatabaseName());
        dataSource.setUser(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }

    private void prepareTable(PostgreSQLContainer<?> container, String tableName) throws Exception {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = DriverManager.getConnection(container.getJdbcUrl(),
                    container.getUsername(), container.getPassword());
            statement = connection.createStatement();
            statement.execute("CREATE TABLE " + tableName + "(id BIGINT PRIMARY KEY, name VARCHAR(64))");
        } finally {
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
