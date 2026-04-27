package com.mysplitter.test;

import com.mysql.jdbc.jdbc2.optional.MysqlXADataSource;
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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

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

public class MySplitterMySqlXaRecoveryIntegrationTest {

    private static MySQLContainer MYSQL_CONTAINER;

    @BeforeClass
    public static void setUpContainer() {
        Assume.assumeTrue(isDockerAvailable());
        MYSQL_CONTAINER = new MySQLContainer("mysql:5.7.34")
                .withDatabaseName("mysplitter_xa")
                .withUsername("test")
                .withPassword("test");
        MYSQL_CONTAINER.start();
    }

    @AfterClass
    public static void tearDownContainer() {
        if (MYSQL_CONTAINER != null) {
            MYSQL_CONTAINER.stop();
        }
    }

    @Test
    public void shouldCommitPreparedMySqlXaBranchDuringFileLogRecovery() throws Exception {
        String tableName = "xa_recovery_commit_" + uniqueSuffix();
        prepareTable(tableName);
        String globalTransactionId = "global_commit_" + uniqueSuffix();
        String branchId = "branch_commit";
        String resourceId = "mysql-writer";
        prepareMySqlXaBranch(tableName, 1, "alpha", globalTransactionId, branchId);
        FileTransactionLogStore logStore = preparedFileLog(globalTransactionId, resourceId, branchId,
                TransactionDecision.COMMIT);

        XaRecoveryReport report = recoverMySqlXaBranch(logStore, resourceId);

        assertEquals(1, report.getRecoveredBranchCount());
        assertEquals(0, report.getUnresolvedBranchCount());
        assertEquals("alpha", queryName(tableName, 1));
        assertEquals(0, logStore.findRecoverable().size());
    }

    @Test
    public void shouldRollbackPreparedMySqlXaBranchDuringFileLogRecovery() throws Exception {
        String tableName = "xa_recovery_rollback_" + uniqueSuffix();
        prepareTable(tableName);
        String globalTransactionId = "global_rollback_" + uniqueSuffix();
        String branchId = "branch_rollback";
        String resourceId = "mysql-writer";
        prepareMySqlXaBranch(tableName, 1, "alpha", globalTransactionId, branchId);
        FileTransactionLogStore logStore = preparedFileLog(globalTransactionId, resourceId, branchId,
                TransactionDecision.ROLLBACK);

        XaRecoveryReport report = recoverMySqlXaBranch(logStore, resourceId);

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

    private void prepareMySqlXaBranch(String tableName,
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
        File logFile = File.createTempFile("mysplitter-mysql-xa-recovery-", ".log");
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

    private XaRecoveryReport recoverMySqlXaBranch(FileTransactionLogStore logStore, String resourceId)
            throws Exception {
        XaResourceRegistry registry = new XaResourceRegistry();
        registry.register(new XaDataSourceAdapter(resourceId, xaDataSource()));
        XaRecoveryExecutor recoveryExecutor = new XaRecoveryExecutor(logStore, registry);
        return recoveryExecutor.recover();
    }

    private MysqlXADataSource xaDataSource() throws Exception {
        MysqlXADataSource dataSource = new MysqlXADataSource();
        dataSource.setUrl(jdbcUrl());
        dataSource.setUser(MYSQL_CONTAINER.getUsername());
        dataSource.setPassword(MYSQL_CONTAINER.getPassword());
        return dataSource;
    }

    private void prepareTable(String tableName) throws Exception {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = DriverManager.getConnection(jdbcUrl(),
                    MYSQL_CONTAINER.getUsername(), MYSQL_CONTAINER.getPassword());
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
            connection = DriverManager.getConnection(jdbcUrl(),
                    MYSQL_CONTAINER.getUsername(), MYSQL_CONTAINER.getPassword());
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
            connection = DriverManager.getConnection(jdbcUrl(),
                    MYSQL_CONTAINER.getUsername(), MYSQL_CONTAINER.getPassword());
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

    private String jdbcUrl() {
        String jdbcUrl = MYSQL_CONTAINER.getJdbcUrl();
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "useSSL=false";
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
