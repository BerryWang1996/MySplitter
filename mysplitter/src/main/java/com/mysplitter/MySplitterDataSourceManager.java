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

package com.mysplitter;

import com.mysplitter.advise.DataSourceFilterAdvise;
import com.mysplitter.advise.DataSourceIllAlerterAdvise;
import com.mysplitter.advise.ReadAndWriteParserAdvise;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterLoadBalanceConfig;
import com.mysplitter.config.MySplitterTransactionConfig;
import com.mysplitter.exceptions.NoHealthyDataSourceException;
import com.mysplitter.selector.LoadBalanceSelector;
import com.mysplitter.selector.NoLoadBalanceSelector;
import com.mysplitter.selector.RandomLoadBalanceSelector;
import com.mysplitter.selector.RoundRobinLoadBalanceSelector;
import com.mysplitter.transaction.GlobalTransactionManager;
import com.mysplitter.transaction.TransactionManagers;
import com.mysplitter.transaction.XaResourceRegistry;
import com.mysplitter.util.ClassLoaderUtil;
import com.mysplitter.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MySplitterDataSourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySplitterDataSourceManager.class);

    private final MySplitterDataSource router;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private final List<DataSourceFilterAdvise> dataSourceFilters = new ArrayList<DataSourceFilterAdvise>();

    private final MySplitterDataSourceRegistry dataSourceRegistry = new MySplitterDataSourceRegistry();

    private MySplitterDatabaseManager databaseManager;

    private ReadAndWriteParserAdvise readAndWriteParser;

    private DataSourceIllAlerterAdvise dataSourceIllAlerter;

    private MySplitterDataSourceHealthManager dataSourceHealthManager;

    private GlobalTransactionManager transactionManager;

    private ScheduledExecutorService transactionRecoveryExecutor;

    MySplitterDataSourceManager(MySplitterDataSource router) throws Exception {
        this.router = router;
        try {
            init();
        } catch (Exception e) {
            try {
                close();
            } catch (Exception closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }

    MySplitterConnectionProxy getConnectionProxy() {
        return new MySplitterConnectionProxy(this);
    }

    MySplitterConnectionProxy getConnectionProxy(String username, String password) {
        return new MySplitterConnectionProxy(this, username, password);
    }

    MySplitterRouteSelection getRouteSelection(MySplitterConnectionContext connectionContext,
                                               MySplitterSqlWrapper sql) throws SQLException {
        return getRouteSelection(connectionContext, sql, null, null);
    }

    MySplitterRouteSelection getRouteSelection(MySplitterConnectionContext connectionContext,
                                               MySplitterSqlWrapper sql,
                                               String username,
                                               String password) throws SQLException {
        String targetDatabase = this.databaseManager.routerHandler(sql.getOriginalSql());
        String rewriteSql = this.databaseManager.rewriteSql(sql.getSql());
        if (rewriteSql != null) {
            sql.rewrite(rewriteSql);
        }

        String operation = this.readAndWriteParser.parseOperation(sql.getSql());
        if (connectionContext.isTransactionActive() && "readers".equals(operation)) {
            operation = "writers";
        }

        MySplitterRouteKey pinnedRoute = connectionContext.getPinnedRoute(targetDatabase);
        if (pinnedRoute != null) {
            Connection connection = connectionContext.getConnection(pinnedRoute);
            if (connection != null) {
                doFilters(pinnedRoute.getDatabaseName(), pinnedRoute.getNodeName(), sql.getSql());
                return new MySplitterRouteSelection(pinnedRoute, connection);
            }
            connectionContext.clearPinnedRoute(targetDatabase);
        }

        MySplitterDataSourceGroup group = dataSourceRegistry.getGroup(targetDatabase, operation);
        if (group == null) {
            throw new IllegalArgumentException("Can not find database:" + targetDatabase + ", operation:" + operation
                    + ". May be databasesRoutingHandler or readAndWriteParser return wrong database or operation.");
        }

        LinkedHashSet<String> attemptedNodeNames = new LinkedHashSet<String>();
        SQLException exceptionHolder = null;
        List<DataSourceWrapper> healthyNodes = new ArrayList<DataSourceWrapper>(dataSourceHealthManager.getHealthyNodes(group));
        while (!healthyNodes.isEmpty()) {
            DataSourceWrapper dataSourceWrapper = selectNextCandidate(group, healthyNodes);
            if (dataSourceWrapper == null) {
                break;
            }
            MySplitterRouteKey routeKey =
                    new MySplitterRouteKey(targetDatabase, group.getNodeGroup(), dataSourceWrapper.getNodeName());
            Connection existing = connectionContext.getConnection(routeKey);
            if (existing != null) {
                doFilters(routeKey.getDatabaseName(), routeKey.getNodeName(), sql.getSql());
                pinRouteIfNecessary(connectionContext, routeKey);
                return new MySplitterRouteSelection(routeKey, existing);
            }
            try {
                doFilters(dataSourceWrapper, sql.getSql());
                Connection connection = transactionManager.openRouteConnection(connectionContext, routeKey,
                        dataSourceWrapper, username, password);
                pinRouteIfNecessary(connectionContext, routeKey);
                return new MySplitterRouteSelection(routeKey, connection);
            } catch (Exception e) {
                attemptedNodeNames.add(dataSourceWrapper.getNodeName());
                handleDataSourceFailure(group, dataSourceWrapper, e);
                exceptionHolder = mergeSqlException(exceptionHolder,
                        toSqlException("Failed to open routed connection.", e));
            }
        }

        return recoverFromIllDataSource(connectionContext, group, targetDatabase, sql.getSql(), username, password,
                exceptionHolder, attemptedNodeNames);
    }

    Connection getDefaultConnection() throws SQLException {
        LOGGER.debug("MySplitter is getting default connection.");
        SQLException exceptionHolder = null;
        for (MySplitterDataSourceGroup group : dataSourceRegistry.listGroups()) {
            Set<String> attemptedNodeNames = new LinkedHashSet<String>();
            List<DataSourceWrapper> healthyNodes =
                    new ArrayList<DataSourceWrapper>(dataSourceHealthManager.getHealthyNodes(group));
            while (!healthyNodes.isEmpty()) {
                DataSourceWrapper dataSourceWrapper = selectNextCandidate(group, healthyNodes);
                if (dataSourceWrapper == null) {
                    break;
                }
                try {
                    return dataSourceWrapper.getRealDataSource().getConnection();
                } catch (SQLException e) {
                    attemptedNodeNames.add(dataSourceWrapper.getNodeName());
                    exceptionHolder = mergeSqlException(exceptionHolder, e);
                    dataSourceHealthManager.markIll(group, dataSourceWrapper, e);
                }
            }

            List<DataSourceWrapper> illNodes =
                    snapshotCandidatesExcluding(dataSourceHealthManager.getIllNodes(group), attemptedNodeNames);
            while (!illNodes.isEmpty()) {
                DataSourceWrapper dataSourceWrapper = selectNextCandidate(group, illNodes);
                if (dataSourceWrapper == null) {
                    break;
                }
                Long illVersion = dataSourceHealthManager.getIllVersion(group, dataSourceWrapper);
                try {
                    Connection connection = dataSourceWrapper.getRealDataSource().getConnection();
                    if (!dataSourceHealthManager.markHealthyIfCurrentVersionMatches(group, dataSourceWrapper, illVersion)) {
                        closeQuietly(connection, dataSourceWrapper);
                        continue;
                    }
                    return connection;
                } catch (SQLException e) {
                    LOGGER.warn("Failed to recover default connection from ill datasource node {} in database {}.",
                            dataSourceWrapper.getNodeName(), dataSourceWrapper.getDataBaseName(), e);
                    exceptionHolder = mergeSqlException(exceptionHolder, e);
                }
            }
        }

        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
        throw new NoHealthyDataSourceException("No healthy data source.");
    }

    void init() throws Exception {
        if (!isInitialized.compareAndSet(false, true)) {
            return;
        }
        LOGGER.debug("MySplitterDataSourceManager is initializing.");
        this.databaseManager = new MySplitterDatabaseManager(this.router);
        createReadAndWriteParser();
        createDataSourceIllAlerter();
        createHealthManager();
        createDataSourceFilters();
        createDataSources();
        createTransactionManager();
        startTransactionRecovery();
    }

    void close() throws Exception {
        Exception closeException = null;
        if (transactionRecoveryExecutor != null) {
            transactionRecoveryExecutor.shutdownNow();
            transactionRecoveryExecutor = null;
        }
        try {
            if (dataSourceHealthManager != null) {
                dataSourceHealthManager.close();
            }
        } catch (Exception e) {
            closeException = mergeException(closeException, e);
        }
        for (DataSourceWrapper dataSourceWrapper : dataSourceRegistry.listAllNodes()) {
            try {
                dataSourceWrapper.releaseRealDataSource();
            } catch (Exception e) {
                closeException = mergeException(closeException, e);
            }
        }
        dataSourceRegistry.clear();
        dataSourceFilters.clear();
        dataSourceHealthManager = null;
        transactionManager = null;
        isInitialized.set(false);
        if (closeException != null) {
            throw closeException;
        }
    }

    GlobalTransactionManager getTransactionManager() {
        return transactionManager;
    }

    private void createTransactionManager() throws SQLException {
        MySplitterTransactionConfig transactionConfig = this.router.getMySplitterConfig().getMysplitter()
                .getTransaction();
        if (transactionConfig != null &&
                MySplitterTransactionConfig.MODE_XA.equalsIgnoreCase(transactionConfig.getMode())) {
            XaResourceRegistry xaResourceRegistry = dataSourceRegistry.createXaResourceRegistry();
            transactionManager = TransactionManagers.create(transactionConfig, xaResourceRegistry);
            return;
        }
        transactionManager = TransactionManagers.create(transactionConfig);
    }

    private void startTransactionRecovery() {
        final MySplitterTransactionConfig transactionConfig = this.router.getMySplitterConfig().getMysplitter()
                .getTransaction();
        if (transactionConfig == null ||
                !MySplitterTransactionConfig.MODE_XA.equalsIgnoreCase(transactionConfig.getMode()) ||
                transactionConfig.getRecovery() == null ||
                !transactionConfig.getRecovery().isEnabled()) {
            return;
        }
        String interval = transactionConfig.getRecovery().getInterval();
        if (StringUtil.isBlank(interval)) {
            interval = "10s";
        }
        transactionRecoveryExecutor = new ScheduledThreadPoolExecutor(1,
                new DaemonThreadFactory("mysplitter xa recovery"));
        transactionRecoveryExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    transactionManager.recover();
                } catch (SQLException e) {
                    LOGGER.warn("MySplitter XA recovery scan failed.", e);
                }
            }
        }, 0L, parseTimePeriod(interval), parseTimeTimeUnit(interval));
    }

    private void createReadAndWriteParser() throws Exception {
        readAndWriteParser = ClassLoaderUtil.getInstance(
                this.router.getMySplitterConfig().getMysplitter().getReadAndWriteParser(),
                ReadAndWriteParserAdvise.class);
    }

    private void createDataSourceIllAlerter() throws Exception {
        dataSourceIllAlerter = ClassLoaderUtil.getInstance(
                this.router.getMySplitterConfig().getMysplitter().getIllAlertHandler(),
                DataSourceIllAlerterAdvise.class);
    }

    private void createHealthManager() {
        dataSourceHealthManager = new MySplitterDataSourceHealthManager(dataSourceIllAlerter);
    }

    private void createDataSourceFilters() throws Exception {
        List<String> filters = this.router.getMySplitterConfig().getMysplitter().getFilters();
        if (filters == null) {
            return;
        }
        for (String filter : filters) {
            dataSourceFilters.add(ClassLoaderUtil.getInstance(filter, DataSourceFilterAdvise.class));
        }
    }

    private void createDataSources() throws Exception {
        Map<String, MySplitterDataBaseConfig> dbs = this.router.getMySplitterConfig().getMysplitter().getDatabases();
        for (String dbKey : dbs.keySet()) {
            MySplitterDataBaseConfig dataBaseConfig = dbs.get(dbKey);
            Map<String, MySplitterLoadBalanceConfig> loadBalanceConfig = dataBaseConfig.getLoadBalance();
            Map<String, MySplitterDataSourceNodeConfig> integrates = dataBaseConfig.getIntegrates();
            if (integrates != null && integrates.size() > 0) {
                createIntegratesDataSource(dbKey, integrates);
            } else {
                Map<String, MySplitterDataSourceNodeConfig> readers = dataBaseConfig.getReaders();
                Map<String, MySplitterDataSourceNodeConfig> writers = dataBaseConfig.getWriters();
                if (readers != null && readers.size() > 0) {
                    createReadersDataSource(dbKey, readers, loadBalanceConfig.get("read"));
                }
                if (writers != null && writers.size() > 0) {
                    createWritersDataSource(dbKey, writers, loadBalanceConfig.get("write"));
                }
            }
        }
        for (DataSourceWrapper dataSourceWrapper : dataSourceRegistry.listAllNodes()) {
            dataSourceWrapper.initRealDataSource();
        }
    }

    private void createReadersDataSource(String dbKey,
                                         Map<String, MySplitterDataSourceNodeConfig> readers,
                                         MySplitterLoadBalanceConfig loadBalanceConfig) {
        MySplitterDataSourceGroup group =
                dataSourceRegistry.createGroup(dbKey, "readers", createLoadBalanceSelector(loadBalanceConfig, readers));
        for (String readerKey : readers.keySet()) {
            MySplitterDataSourceNodeConfig nodeConfig = readers.get(readerKey);
            DataSourceWrapper wrapper = new DataSourceWrapper(readerKey, dbKey, "readers", nodeConfig,
                    loadBalanceConfig);
            group.register(wrapper, nodeConfig.getWeight());
        }
    }

    private void createWritersDataSource(String dbKey,
                                         Map<String, MySplitterDataSourceNodeConfig> writers,
                                         MySplitterLoadBalanceConfig loadBalanceConfig) {
        MySplitterDataSourceGroup group =
                dataSourceRegistry.createGroup(dbKey, "writers", createLoadBalanceSelector(loadBalanceConfig, writers));
        for (String writerKey : writers.keySet()) {
            MySplitterDataSourceNodeConfig nodeConfig = writers.get(writerKey);
            DataSourceWrapper wrapper = new DataSourceWrapper(writerKey, dbKey, "writers", nodeConfig,
                    loadBalanceConfig);
            group.register(wrapper, nodeConfig.getWeight());
        }
    }

    private void createIntegratesDataSource(String dbKey,
                                            Map<String, MySplitterDataSourceNodeConfig> integrates) {
        if (integrates.size() > 1) {
            throw new IllegalArgumentException("The database named " + dbKey + " contains " + integrates.size()
                    + " datasource nodes.");
        }
        MySplitterDataSourceGroup group =
                dataSourceRegistry.createGroup(dbKey, "integrates", new NoLoadBalanceSelector<DataSourceWrapper>());
        for (String integrateKey : integrates.keySet()) {
            MySplitterDataSourceNodeConfig nodeConfig = integrates.get(integrateKey);
            DataSourceWrapper wrapper = new DataSourceWrapper(integrateKey, dbKey, "integrates", nodeConfig, null);
            group.register(wrapper, nodeConfig.getWeight());
        }
    }

    private LoadBalanceSelector<DataSourceWrapper> createLoadBalanceSelector(
            MySplitterLoadBalanceConfig loadBalanceConfig,
            Map<String, MySplitterDataSourceNodeConfig> readersOrWriters) {
        if (loadBalanceConfig.isEnabled() && readersOrWriters.size() > 1) {
            if ("polling".equals(loadBalanceConfig.getStrategy())) {
                return new RoundRobinLoadBalanceSelector<DataSourceWrapper>();
            }
            if ("random".equals(loadBalanceConfig.getStrategy())) {
                return new RandomLoadBalanceSelector<DataSourceWrapper>();
            }
        }
        return new NoLoadBalanceSelector<DataSourceWrapper>();
    }

    private MySplitterRouteSelection recoverFromIllDataSource(MySplitterConnectionContext connectionContext,
                                                              MySplitterDataSourceGroup group,
                                                              String targetDatabase,
                                                              String sql,
                                                              String username,
                                                              String password,
                                                              SQLException currentException,
                                                              Set<String> attemptedNodeNames) throws SQLException {
        List<DataSourceWrapper> illNodes =
                snapshotCandidatesExcluding(dataSourceHealthManager.getIllNodes(group), attemptedNodeNames);
        if (illNodes.size() == 0) {
            if (currentException != null) {
                throw currentException;
            }
            throw new NoHealthyDataSourceException("No data source node was found.");
        }

        SQLException exceptionHolder = currentException;
        while (!illNodes.isEmpty()) {
            DataSourceWrapper dataSourceWrapper = selectNextCandidate(group, illNodes);
            if (dataSourceWrapper == null) {
                break;
            }
            Long illVersion = dataSourceHealthManager.getIllVersion(group, dataSourceWrapper);
            MySplitterRouteKey routeKey =
                    new MySplitterRouteKey(targetDatabase, group.getNodeGroup(), dataSourceWrapper.getNodeName());
            Connection existing = connectionContext.getConnection(routeKey);
            if (existing != null) {
                doFilters(routeKey.getDatabaseName(), routeKey.getNodeName(), sql);
                dataSourceHealthManager.markHealthyIfCurrentVersionMatches(group, dataSourceWrapper, illVersion);
                pinRouteIfNecessary(connectionContext, routeKey);
                return new MySplitterRouteSelection(routeKey, existing);
            }
            try {
                doFilters(dataSourceWrapper, sql);
                Connection connection = transactionManager.openRouteConnection(connectionContext, routeKey,
                        dataSourceWrapper, username, password);
                if (!dataSourceHealthManager.markHealthyIfCurrentVersionMatches(group, dataSourceWrapper, illVersion)) {
                    closeQuietly(connection, dataSourceWrapper);
                    connectionContext.removeConnection(routeKey);
                    continue;
                }
                pinRouteIfNecessary(connectionContext, routeKey);
                return new MySplitterRouteSelection(routeKey, connection);
            } catch (Exception e) {
                LOGGER.warn("Failed to recover connection from ill datasource node {} in database {}.",
                        dataSourceWrapper.getNodeName(), dataSourceWrapper.getDataBaseName(), e);
                exceptionHolder = mergeSqlException(exceptionHolder,
                        toSqlException("Failed to recover connection from ill datasource.", e));
            }
        }

        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
        throw new NoHealthyDataSourceException("No data source node was found.");
    }

    private void pinRouteIfNecessary(MySplitterConnectionContext connectionContext, MySplitterRouteKey routeKey) {
        if (connectionContext.isTransactionActive() && !"readers".equals(routeKey.getNodeGroup())) {
            connectionContext.pinRoute(routeKey.getDatabaseName(), routeKey);
        }
    }

    private void handleDataSourceFailure(MySplitterDataSourceGroup group,
                                         DataSourceWrapper dataSourceWrapper,
                                         Exception exception) {
        if (dataSourceWrapper == null) {
            return;
        }
        LOGGER.warn("MySplitter failed to get connection from database {}, operation {}, node {}. Retrying with other nodes.",
                group.getDatabaseName(), group.getNodeGroup(), dataSourceWrapper.getNodeName(), exception);
        dataSourceHealthManager.markIll(group, dataSourceWrapper, exception);
    }

    private void doFilters(DataSourceWrapper dataSourceWrapper, String sql) throws SQLException {
        doFilters(dataSourceWrapper.getDataBaseName(), dataSourceWrapper.getNodeName(), sql);
    }

    private void doFilters(String databaseName, String nodeName, String sql) throws SQLException {
        for (DataSourceFilterAdvise dataSourceFilter : dataSourceFilters) {
            try {
                dataSourceFilter.doFilter(databaseName, nodeName, sql);
            } catch (Exception e) {
                throw new SQLException("Execute datasource filter failed.", e);
            }
        }
    }

    private DataSourceWrapper selectNextCandidate(MySplitterDataSourceGroup group, List<DataSourceWrapper> candidates) {
        DataSourceWrapper dataSourceWrapper = group.acquire(candidates);
        if (dataSourceWrapper == null) {
            return null;
        }
        removeCandidate(candidates, dataSourceWrapper);
        return dataSourceWrapper;
    }

    private List<DataSourceWrapper> snapshotCandidatesExcluding(List<DataSourceWrapper> candidates,
                                                                Set<String> excludedNodeNames) {
        List<DataSourceWrapper> filtered = new ArrayList<DataSourceWrapper>();
        for (DataSourceWrapper candidate : candidates) {
            if (excludedNodeNames != null && excludedNodeNames.contains(candidate.getNodeName())) {
                continue;
            }
            filtered.add(candidate);
        }
        return filtered;
    }

    private void removeCandidate(List<DataSourceWrapper> candidates, DataSourceWrapper target) {
        for (int i = 0; i < candidates.size(); i++) {
            DataSourceWrapper candidate = candidates.get(i);
            if (candidate == target || candidate.getNodeName().equals(target.getNodeName())) {
                candidates.remove(i);
                return;
            }
        }
    }

    private void closeQuietly(Connection connection, DataSourceWrapper dataSourceWrapper) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            LOGGER.warn("Failed to close discarded recovery connection from datasource node {} in database {}.",
                    dataSourceWrapper.getNodeName(), dataSourceWrapper.getDataBaseName(), e);
        }
    }

    private Exception mergeException(Exception current, Exception next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private SQLException mergeSqlException(SQLException current, SQLException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private SQLException toSqlException(String message, Exception exception) {
        if (exception instanceof SQLException) {
            return (SQLException) exception;
        }
        return new SQLException(message, exception);
    }

    private Integer parseTimePeriod(String time) {
        return Integer.parseInt(time.substring(0, time.length() - 1));
    }

    private TimeUnit parseTimeTimeUnit(String time) {
        String suffix = time.substring(time.length() - 1);
        if ("s".equalsIgnoreCase(suffix)) {
            return TimeUnit.SECONDS;
        }
        if ("m".equalsIgnoreCase(suffix)) {
            return TimeUnit.MINUTES;
        }
        if ("h".equalsIgnoreCase(suffix)) {
            return TimeUnit.HOURS;
        }
        return TimeUnit.SECONDS;
    }

    Map<String, Object> getStatus() {
        if (dataSourceHealthManager == null) {
            return new java.util.HashMap<String, Object>();
        }
        return dataSourceHealthManager.getStatus(dataSourceRegistry);
    }
}
