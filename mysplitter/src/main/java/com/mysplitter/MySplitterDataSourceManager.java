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
import com.mysplitter.exceptions.NoHealthyDataSourceException;
import com.mysplitter.selector.LoadBalanceSelector;
import com.mysplitter.selector.NoLoadBalanceSelector;
import com.mysplitter.selector.RandomLoadBalanceSelector;
import com.mysplitter.selector.RoundRobinLoadBalanceSelector;
import com.mysplitter.util.ClassLoaderUtil;
import com.mysplitter.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MySplitterDataSourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySplitterDataSourceManager.class);

    private final MySplitterDataSource router;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private final List<DataSourceFilterAdvise> dataSourceFilters = new ArrayList<DataSourceFilterAdvise>();

    private final Map<String, LoadBalanceSelector<DataSourceWrapper>> healthyDataSourceSelectorMap =
            new ConcurrentHashMap<String, LoadBalanceSelector<DataSourceWrapper>>();

    private final Map<String, LoadBalanceSelector<DataSourceWrapper>> illDataSourceSelectorMap =
            new ConcurrentHashMap<String, LoadBalanceSelector<DataSourceWrapper>>();

    private MySplitterDatabaseManager databaseManager;

    private ReadAndWriteParserAdvise readAndWriteParser;

    private DataSourceIllAlerterAdvise dataSourceIllAlerter;

    private ScheduledThreadPoolExecutor illDataSourceFailTimeoutExecutor;

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

    Connection getConnection(MySplitterSqlWrapper sql) throws SQLException {
        return getConnection(sql, null, null);
    }

    Connection getConnection(MySplitterSqlWrapper sql, String username, String password) throws SQLException {
        String targetDatabase = this.databaseManager.routerHandler(sql.getOriginalSql());
        String rewriteSql = this.databaseManager.rewriteSql(sql.getSql());
        if (rewriteSql != null) {
            sql.rewrite(rewriteSql);
        }
        String operation = this.readAndWriteParser.parseOperation(sql.getSql());
        LoadBalanceSelector<DataSourceWrapper> healthySelector = getSelector(healthyDataSourceSelectorMap,
                targetDatabase, operation);
        if (healthySelector == null) {
            throw new IllegalArgumentException("Can not find database:" + targetDatabase + ", operation:" + operation
                    + ". May be databasesRoutingHandler or readAndWriteParser return wrong database or operation.");
        }

        DataSourceWrapper dataSourceWrapper = null;
        try {
            dataSourceWrapper = healthySelector.acquire();
            if (dataSourceWrapper == null) {
                throw new NoHealthyDataSourceException();
            }
            doFilters(dataSourceWrapper, sql.getSql());
            return openConnection(dataSourceWrapper, username, password);
        } catch (NoHealthyDataSourceException e) {
            return recoverFromIllDataSource(targetDatabase, operation, sql.getSql(), username, password);
        } catch (Exception e) {
            handleDataSourceFailure(targetDatabase, operation, healthySelector, dataSourceWrapper, e);
            return getConnection(sql, username, password);
        }
    }

    Connection getDefaultConnection() throws SQLException {
        LOGGER.debug("MySplitter is getting default connection.");
        SQLException exceptionHolder = null;
        for (Map.Entry<String, LoadBalanceSelector<DataSourceWrapper>> entry : healthyDataSourceSelectorMap.entrySet()) {
            for (DataSourceWrapper dataSourceWrapper : uniqueDataSources(entry.getValue())) {
                try {
                    return dataSourceWrapper.getRealDataSource().getConnection();
                } catch (SQLException e) {
                    exceptionHolder = e;
                    entry.getValue().release(dataSourceWrapper);
                    LoadBalanceSelector<DataSourceWrapper> illSelector = illDataSourceSelectorMap.get(entry.getKey());
                    if (illSelector != null) {
                        illSelector.register(dataSourceWrapper, dataSourceWrapper.getNodeConfig().getWeight());
                    }
                    dataSourceIllAlerter.alert(dataSourceWrapper.getDataBaseName(), dataSourceWrapper.getNodeName(), e);
                    submitDataSourceFailTimeoutTask(dataSourceWrapper.getDataBaseName(),
                            entry.getKey().split(":")[1], dataSourceWrapper);
                }
            }
        }

        for (Map.Entry<String, LoadBalanceSelector<DataSourceWrapper>> entry : illDataSourceSelectorMap.entrySet()) {
            for (DataSourceWrapper dataSourceWrapper : uniqueDataSources(entry.getValue())) {
                try {
                    Connection connection = dataSourceWrapper.getRealDataSource().getConnection();
                    entry.getValue().release(dataSourceWrapper);
                    LoadBalanceSelector<DataSourceWrapper> healthySelector =
                            healthyDataSourceSelectorMap.get(entry.getKey());
                    if (healthySelector != null) {
                        healthySelector.register(dataSourceWrapper, dataSourceWrapper.getNodeConfig().getWeight());
                    }
                    return connection;
                } catch (SQLException e) {
                    LOGGER.warn("Failed to recover default connection from ill datasource node {} in database {}.",
                            dataSourceWrapper.getNodeName(), dataSourceWrapper.getDataBaseName(), e);
                    if (exceptionHolder == null) {
                        exceptionHolder = e;
                    }
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
        createDataSourceFilters();
        createIllDataSourceFailTimeoutExecutor();
        createDataSources();
    }

    void close() throws Exception {
        Exception closeException = null;
        Set<DataSourceWrapper> released = new HashSet<DataSourceWrapper>();
        try {
            if (illDataSourceFailTimeoutExecutor != null) {
                illDataSourceFailTimeoutExecutor.shutdownNow();
            }
        } catch (Exception e) {
            closeException = mergeException(closeException, e);
        }
        try {
            release(healthyDataSourceSelectorMap, released);
        } catch (Exception e) {
            closeException = mergeException(closeException, e);
        }
        try {
            release(illDataSourceSelectorMap, released);
        } catch (Exception e) {
            closeException = mergeException(closeException, e);
        }
        healthyDataSourceSelectorMap.clear();
        illDataSourceSelectorMap.clear();
        dataSourceFilters.clear();
        illDataSourceFailTimeoutExecutor = null;
        isInitialized.set(false);
        if (closeException != null) {
            throw closeException;
        }
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

    private void createDataSourceFilters() throws Exception {
        List<String> filters = this.router.getMySplitterConfig().getMysplitter().getFilters();
        if (filters == null) {
            return;
        }
        for (String filter : filters) {
            dataSourceFilters.add(ClassLoaderUtil.getInstance(filter, DataSourceFilterAdvise.class));
        }
    }

    private void createIllDataSourceFailTimeoutExecutor() {
        illDataSourceFailTimeoutExecutor =
                new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                        new DaemonThreadFactory("mysplitter fail timeout"));
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
        for (LoadBalanceSelector<DataSourceWrapper> selector : healthyDataSourceSelectorMap.values()) {
            for (DataSourceWrapper dataSourceWrapper : uniqueDataSources(selector)) {
                dataSourceWrapper.initRealDataSource();
            }
        }
    }

    private void createReadersDataSource(String dbKey,
                                         Map<String, MySplitterDataSourceNodeConfig> readers,
                                         MySplitterLoadBalanceConfig loadBalanceConfig) {
        String selectorName = generateDataSourceSelectorName(dbKey, "readers");
        createLoadBalanceSelector(selectorName, loadBalanceConfig, readers);
        for (String readerKey : readers.keySet()) {
            MySplitterDataSourceNodeConfig nodeConfig = readers.get(readerKey);
            DataSourceWrapper wrapper = new DataSourceWrapper(readerKey, dbKey, nodeConfig, loadBalanceConfig);
            healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
        }
    }

    private void createWritersDataSource(String dbKey,
                                         Map<String, MySplitterDataSourceNodeConfig> writers,
                                         MySplitterLoadBalanceConfig loadBalanceConfig) {
        String selectorName = generateDataSourceSelectorName(dbKey, "writers");
        createLoadBalanceSelector(selectorName, loadBalanceConfig, writers);
        for (String writerKey : writers.keySet()) {
            MySplitterDataSourceNodeConfig nodeConfig = writers.get(writerKey);
            DataSourceWrapper wrapper = new DataSourceWrapper(writerKey, dbKey, nodeConfig, loadBalanceConfig);
            healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
        }
    }

    private void createIntegratesDataSource(String dbKey,
                                            Map<String, MySplitterDataSourceNodeConfig> integrates) {
        if (integrates.size() > 1) {
            throw new IllegalArgumentException("The database named " + dbKey + " contains " + integrates.size()
                    + " datasource nodes.");
        }
        String selectorName = generateDataSourceSelectorName(dbKey, "integrates");
        healthyDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
        illDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
        for (String integrateKey : integrates.keySet()) {
            MySplitterDataSourceNodeConfig nodeConfig = integrates.get(integrateKey);
            DataSourceWrapper wrapper = new DataSourceWrapper(integrateKey, dbKey, nodeConfig, null);
            healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
        }
    }

    private void createLoadBalanceSelector(String selectorName,
                                           MySplitterLoadBalanceConfig loadBalanceConfig,
                                           Map<String, MySplitterDataSourceNodeConfig> readersOrWriters) {
        if (loadBalanceConfig.isEnabled() && readersOrWriters.size() > 1) {
            if ("polling".equals(loadBalanceConfig.getStrategy())) {
                healthyDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
                illDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
                return;
            }
            if ("random".equals(loadBalanceConfig.getStrategy())) {
                healthyDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
                illDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
                return;
            }
        }
        healthyDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
        illDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
    }

    private Connection recoverFromIllDataSource(String targetDatabase,
                                                String operation,
                                                String sql,
                                                String username,
                                                String password) throws SQLException {
        LoadBalanceSelector<DataSourceWrapper> illSelector = getSelector(illDataSourceSelectorMap,
                targetDatabase, operation);
        if (illSelector == null) {
            throw new NoHealthyDataSourceException("No data source node was found.");
        }

        List<DataSourceWrapper> dataSourceWrappers = uniqueDataSources(illSelector);
        if (dataSourceWrappers.size() == 0) {
            throw new NoHealthyDataSourceException("No data source node was found.");
        }

        Exception lastException = null;
        for (DataSourceWrapper dataSourceWrapper : dataSourceWrappers) {
            try {
                doFilters(dataSourceWrapper, sql);
                Connection connection = openConnection(dataSourceWrapper, username, password);
                illSelector.release(dataSourceWrapper);
                LoadBalanceSelector<DataSourceWrapper> healthySelector =
                        getSelector(healthyDataSourceSelectorMap, targetDatabase, operation);
                if (healthySelector != null) {
                    healthySelector.register(dataSourceWrapper, dataSourceWrapper.getNodeConfig().getWeight());
                }
                return connection;
            } catch (Exception e) {
                LOGGER.warn("Failed to recover connection from ill datasource node {} in database {}.",
                        dataSourceWrapper.getNodeName(), dataSourceWrapper.getDataBaseName(), e);
                lastException = e;
            }
        }

        if (lastException instanceof SQLException) {
            throw (SQLException) lastException;
        }
        throw new SQLException("Failed to recover connection from ill datasource.", lastException);
    }

    private void handleDataSourceFailure(String targetDatabase,
                                         String operation,
                                         LoadBalanceSelector<DataSourceWrapper> healthySelector,
                                         DataSourceWrapper dataSourceWrapper,
                                         Exception exception) {
        if (dataSourceWrapper == null) {
            return;
        }
        LOGGER.warn("MySplitter failed to get connection from database {}, operation {}, node {}. Retrying with other nodes.",
                targetDatabase, operation, dataSourceWrapper.getNodeName(), exception);
        healthySelector.release(dataSourceWrapper);
        LoadBalanceSelector<DataSourceWrapper> illSelector =
                getSelector(illDataSourceSelectorMap, targetDatabase, operation);
        if (illSelector != null) {
            illSelector.register(dataSourceWrapper, dataSourceWrapper.getNodeConfig().getWeight());
        }
        dataSourceIllAlerter.alert(dataSourceWrapper.getDataBaseName(), dataSourceWrapper.getNodeName(), exception);
        submitDataSourceFailTimeoutTask(targetDatabase, operation, dataSourceWrapper);
    }

    private Connection openConnection(DataSourceWrapper dataSourceWrapper,
                                      String username,
                                      String password) throws SQLException {
        if (username != null || password != null) {
            return dataSourceWrapper.getRealDataSource().getConnection(username, password);
        }
        return dataSourceWrapper.getRealDataSource().getConnection();
    }

    private void doFilters(DataSourceWrapper dataSourceWrapper, String sql) throws SQLException {
        for (DataSourceFilterAdvise dataSourceFilter : dataSourceFilters) {
            try {
                dataSourceFilter.doFilter(dataSourceWrapper.getDataBaseName(), dataSourceWrapper.getNodeName(), sql);
            } catch (Exception e) {
                throw new SQLException("Execute datasource filter failed.", e);
            }
        }
    }

    private LoadBalanceSelector<DataSourceWrapper> getSelector(
            Map<String, LoadBalanceSelector<DataSourceWrapper>> selectorMap,
            String targetDatabase,
            String operation) {
        LoadBalanceSelector<DataSourceWrapper> selector =
                selectorMap.get(generateDataSourceSelectorName(targetDatabase, operation));
        if (selector == null) {
            selector = selectorMap.get(generateDataSourceSelectorName(targetDatabase, "integrates"));
        }
        return selector;
    }

    private void release(Map<String, LoadBalanceSelector<DataSourceWrapper>> selectorMap,
                         Set<DataSourceWrapper> released) throws Exception {
        Exception releaseException = null;
        for (Map.Entry<String, LoadBalanceSelector<DataSourceWrapper>> entry : selectorMap.entrySet()) {
            for (DataSourceWrapper dataSourceWrapper : uniqueDataSources(entry.getValue())) {
                if (released.add(dataSourceWrapper)) {
                    try {
                        dataSourceWrapper.releaseRealDataSource();
                    } catch (Exception e) {
                        releaseException = mergeException(releaseException, e);
                    }
                }
            }
        }
        if (releaseException != null) {
            throw releaseException;
        }
    }

    private List<DataSourceWrapper> uniqueDataSources(LoadBalanceSelector<DataSourceWrapper> selector) {
        LinkedHashSet<DataSourceWrapper> wrappers = new LinkedHashSet<DataSourceWrapper>(selector.listAll());
        return new ArrayList<DataSourceWrapper>(wrappers);
    }

    private String generateDataSourceSelectorName(String databaseName, String operation) {
        return databaseName + ":" + operation;
    }

    private Exception mergeException(Exception current, Exception next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private void submitDataSourceFailTimeoutTask(final String targetDatabase,
                                                 final String operation,
                                                 final DataSourceWrapper dataSourceWrapper) {
        final String time;
        if (dataSourceWrapper.getLoadBalanceConfig() == null
                || StringUtil.isBlank(dataSourceWrapper.getLoadBalanceConfig().getFailTimeout())) {
            time = "30s";
        } else {
            time = dataSourceWrapper.getLoadBalanceConfig().getFailTimeout();
        }
        illDataSourceFailTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                LoadBalanceSelector<DataSourceWrapper> illSelector =
                        getSelector(illDataSourceSelectorMap, targetDatabase, operation);
                if (illSelector == null || illSelector.listAll().size() == 0) {
                    return;
                }
                illSelector.release(dataSourceWrapper);
                LoadBalanceSelector<DataSourceWrapper> healthySelector =
                        getSelector(healthyDataSourceSelectorMap, targetDatabase, operation);
                if (healthySelector != null) {
                    healthySelector.register(dataSourceWrapper, dataSourceWrapper.getNodeConfig().getWeight());
                }
            }
        }, parseTimePeriod(time), parseTimeTimeUnit(time));
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
        Map<String, Object> status = new HashMap<String, Object>();
        Map<String, Object> healthy = new TreeMap<String, Object>();
        for (Map.Entry<String, LoadBalanceSelector<DataSourceWrapper>> entry : healthyDataSourceSelectorMap.entrySet()) {
            LinkedHashSet<String> data = new LinkedHashSet<String>();
            for (DataSourceWrapper dataSourceWrapper : entry.getValue().listAll()) {
                data.add(dataSourceWrapper.getNodeName());
            }
            healthy.put(entry.getKey(), new ArrayList<String>(data));
        }
        status.put("healthy", healthy);

        Map<String, Object> ill = new TreeMap<String, Object>();
        for (Map.Entry<String, LoadBalanceSelector<DataSourceWrapper>> entry : illDataSourceSelectorMap.entrySet()) {
            LinkedHashSet<String> data = new LinkedHashSet<String>();
            for (DataSourceWrapper dataSourceWrapper : entry.getValue().listAll()) {
                data.add(dataSourceWrapper.getNodeName());
            }
            ill.put(entry.getKey(), new ArrayList<String>(data));
        }
        status.put("ill", ill);
        return status;
    }
}
