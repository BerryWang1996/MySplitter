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

import com.mysplitter.advise.DataSourceIllAlerterAdvise;
import com.mysplitter.advise.ReadAndWriteParserAdvise;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterLoadBalanceConfig;
import com.mysplitter.exceptions.NoHealthyDataSourceException;
import com.mysplitter.selector.AbstractLoadBalanceSelector;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MySplitter数据源管理器，用于获取真实数据源，并执行高可用以及负载均衡等实现
 */
public class MySplitterDataSourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySplitterDataSourceManager.class);

    private MySplitterDataSource router;

    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    private MySplitterDatabaseManager databaseManager;

    private ReadAndWriteParserAdvise readAndWriteParser;

    private DataSourceIllAlerterAdvise dataSourceIllAlerter;

    private Map<String, AbstractLoadBalanceSelector<DataSourceWrapper>> healthyDataSourceSelectorMap =
            new ConcurrentHashMap<String, AbstractLoadBalanceSelector<DataSourceWrapper>>();

    private Map<String, AbstractLoadBalanceSelector<DataSourceWrapper>> illDataSourceSelectorMap =
            new ConcurrentHashMap<String, AbstractLoadBalanceSelector<DataSourceWrapper>>();

    private ScheduledThreadPoolExecutor illDataSourceFailTimeoutExecutor;

    MySplitterDataSourceManager(MySplitterDataSource router) throws Exception {
        this.router = router;
        init();
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
        String targetDatabase = this.databaseManager.routerHandler(sql.getSql());
        String rewriteSql = this.databaseManager.rewriteSql(sql.getSql());
        if (rewriteSql != null) {
            sql.setSql(rewriteSql);
        }
        String operation = this.readAndWriteParser.parseOperation(sql.getSql());
        AbstractLoadBalanceSelector<DataSourceWrapper> healthySelector =
                this.healthyDataSourceSelectorMap.get(generateDataSourceSelectorName(targetDatabase, operation));
        if (healthySelector == null) {
            healthySelector =
                    this.healthyDataSourceSelectorMap.get(generateDataSourceSelectorName(targetDatabase, "integrates"));
        }
        if (healthySelector == null) {
            throw new IllegalArgumentException("Can not find database:" + targetDatabase + ", operation:" + operation
                    + ". May be databasesRoutingHandler or readAndWriteParser return wrong database or operation.");
        }
        Connection realConnection = null;
        DataSourceWrapper dataSourceWrapper = null;
        try {
            dataSourceWrapper = healthySelector.acquire();
            if (dataSourceWrapper == null) {
                throw new NoHealthyDataSourceException();
            }
            if (username != null || password != null) {
                realConnection = dataSourceWrapper.getRealDataSource().getConnection(username, password);
            } else {
                realConnection = dataSourceWrapper.getRealDataSource().getConnection();
            }
        } catch (NoHealthyDataSourceException e) {
            // 如果一个健康的数据源都没有了，尝试从不健康的数据源获取
            AbstractLoadBalanceSelector<DataSourceWrapper> illSelector =
                    illDataSourceSelectorMap.get(generateDataSourceSelectorName(targetDatabase, operation));
            if (illSelector == null) {
                illSelector = this.healthyDataSourceSelectorMap.get(generateDataSourceSelectorName(targetDatabase,
                        "integrates"));
            }
            List<DataSourceWrapper> dataSourceWrappers = illSelector.listAll();
            int totalIllCount = dataSourceWrappers.size();
            // 此时可能会出现异常
            for (int i = 0; i < totalIllCount; i++) {
                dataSourceWrapper = dataSourceWrappers.get(i);
                try {
                    if (username != null || password != null) {
                        realConnection = dataSourceWrapper.getRealDataSource().getConnection(username, password);
                    } else {
                        realConnection = dataSourceWrapper.getRealDataSource().getConnection();
                    }
                    // 如果没出现异常，放入健康数据源map
                    illSelector.release(dataSourceWrapper);
                    this.healthyDataSourceSelectorMap
                            .get(generateDataSourceSelectorName(targetDatabase, operation))
                            .register(dataSourceWrapper, dataSourceWrapper.getNodeConfig().getWeight());
                    // 跳出循环
                    break;
                } catch (Exception e1) {
                    // 如果遍历到最后一个数据源还是出现异常不再进行处理（抛出异常），其他的忽略
                    if (totalIllCount == i - 1) {
                        throw e1;
                    }
                }
            }
        } catch (SQLException e1) {
            // 如果获取连接出现异常，放入连接异常数据源map
            healthySelector.release(dataSourceWrapper);
            this.illDataSourceSelectorMap
                    .get(generateDataSourceSelectorName(targetDatabase, operation))
                    .register(dataSourceWrapper, dataSourceWrapper.getNodeConfig().getWeight());
            // 异常提醒
            dataSourceIllAlerter.alert(dataSourceWrapper.getDataBaseName(), dataSourceWrapper.getNodeName(), e1);
            // 提交到期自动移入健康数据源的任务
            submitDataSourceFailTimeoutTask(targetDatabase, operation, dataSourceWrapper);
        }
        return realConnection;
    }

    Connection getDefaultConnection() throws SQLException {
        return this.healthyDataSourceSelectorMap
                .get(new ArrayList<>(this.healthyDataSourceSelectorMap.keySet()).get(0))
                .acquire()
                .getRealDataSource()
                .getConnection();
    }

    void init() throws Exception {
        if (isInitialized.compareAndSet(false, true)) {
            LOGGER.debug("MySplitterDataSourceManager is initializing.");
            // 创建多数据库管理器
            this.databaseManager = new MySplitterDatabaseManager(this.router);
            // 创建读写解析器
            createReadAndWriteParser();
            // 创建数据源异常提醒
            createDataSourceIllAlerter();
            // 创建异常数据源定时任务执行线程池
            createIllDataSourceFailTimeoutExecutor();
            // 创建数据源节点
            createDataSources();
        }
    }

    void close() throws Exception {
        release(healthyDataSourceSelectorMap);
        release(illDataSourceSelectorMap);
    }

    private void release(Map<String, AbstractLoadBalanceSelector<DataSourceWrapper>> selectorMap) throws Exception {
        for (Map.Entry<String, AbstractLoadBalanceSelector<DataSourceWrapper>> entry : selectorMap.entrySet()) {
            for (DataSourceWrapper dataSourceWrapper : entry.getValue().listAll()) {
                dataSourceWrapper.releaseRealDataSource();
            }
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

    private void createIllDataSourceFailTimeoutExecutor() {
        illDataSourceFailTimeoutExecutor =
                new ScheduledThreadPoolExecutor(
                        Runtime.getRuntime().availableProcessors(),
                        new DaemonThreadFactory("mysplitter fail timeout"));
    }

    private void createDataSources() {
        LOGGER.debug("MySplitterDataSourceManager is getting data sources configuration.");
        // 获取所有的数据库配置，根据读写创建对应的数据源wrapper
        Map<String, MySplitterDataBaseConfig> dbs = this.router.getMySplitterConfig().getMysplitter().getDatabases();
        for (String dbKey : dbs.keySet()) {
            // 获取数据库的配置
            MySplitterDataBaseConfig dataBaseConfig = dbs.get(dbKey);
            // 获取数据库负载均衡配置
            Map<String, MySplitterLoadBalanceConfig> loadBalanceConfig = dataBaseConfig.getLoadBalance();
            // 获取数据源并进行初始化
            Map<String, MySplitterDataSourceNodeConfig> integrates = dataBaseConfig.getIntegrates();
            if (integrates != null && integrates.size() > 0) {
                // 如果整合数据源存在
                createIntegratesDataSource(dbKey, integrates);
            } else {
                Map<String, MySplitterDataSourceNodeConfig> readers = dataBaseConfig.getReaders();
                Map<String, MySplitterDataSourceNodeConfig> writers = dataBaseConfig.getWriters();
                // 如果整合数据源不存在，读取读和写数据源
                if (readers != null && readers.size() > 0) {
                    createReadersDataSource(dbKey, readers, loadBalanceConfig.get("read"));
                }
                if (writers != null && writers.size() > 0) {
                    createWritersDataSource(dbKey, writers, loadBalanceConfig.get("write"));
                }
            }
        }
        // 打印所有数据源的列表
        LOGGER.debug("healthyDataSourceSelectorMap:{}",
                healthyDataSourceSelectorMap.entrySet());
        LOGGER.debug("illDataSourceSelectorMap    :{}",
                illDataSourceSelectorMap.entrySet());
        // 初始化准备激活的数据源wrapper
        for (String healthyDataSourceKey : healthyDataSourceSelectorMap.keySet()) {
            AbstractLoadBalanceSelector<DataSourceWrapper> selector =
                    healthyDataSourceSelectorMap.get(healthyDataSourceKey);
            List<DataSourceWrapper> dataSourceWrappers = selector.listAll();
            for (DataSourceWrapper dataSourceWrapper : dataSourceWrappers) {
                dataSourceWrapper.initRealDataSource();
            }
        }
    }

    private void createReadersDataSource(String dbKey,
                                         Map<String, MySplitterDataSourceNodeConfig> readers,
                                         MySplitterLoadBalanceConfig loadBalanceConfig) {
        // 创建选择器
        String selectorName = generateDataSourceSelectorName(dbKey, "readers");
        // 根据负载均衡设置进行设置
        createLoadBalanceSelector(selectorName, loadBalanceConfig, readers);
        // 创建所有的数据源到healthyDataSourceSelector
        for (String readerKey : readers.keySet()) {
            // 获取节点配置
            MySplitterDataSourceNodeConfig nodeConfig = readers.get(readerKey);
            // 创建包装类对象
            DataSourceWrapper wrapper = new DataSourceWrapper(readerKey, dbKey, nodeConfig, loadBalanceConfig);
            // 放入选择器
            healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
            LOGGER.debug("MySplitter has been registered {} data source node: Database:{}, Node:{}",
                    "read", dbKey, readerKey);
        }
    }

    private void createWritersDataSource(String dbKey,
                                         Map<String, MySplitterDataSourceNodeConfig> writers,
                                         MySplitterLoadBalanceConfig loadBalanceConfig) {
        // 创建选择器
        String selectorName = generateDataSourceSelectorName(dbKey, "writers");
        // 根据负载均衡设置进行设置
        createLoadBalanceSelector(selectorName, loadBalanceConfig, writers);
        // 创建所有的数据源到healthyDataSourceSelector
        for (String writerKey : writers.keySet()) {
            // 获取节点配置
            MySplitterDataSourceNodeConfig nodeConfig = writers.get(writerKey);
            // 创建包装类对象
            DataSourceWrapper wrapper = new DataSourceWrapper(writerKey, dbKey, nodeConfig, loadBalanceConfig);
            // 放入选择器
            healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
            LOGGER.debug("MySplitter has been registered {} data source node: Database:{}, Node:{}",
                    "write", dbKey, writerKey);
        }
    }

    private void createIntegratesDataSource(String dbKey,
                                            Map<String, MySplitterDataSourceNodeConfig> integrates) {
        // 如果整合数据源超过一个，抛出异常
        if (integrates.size() > 1) {
            throw new IllegalArgumentException("The database named " + dbKey + " contains " + integrates.size() + " " +
                    "datasource nodes.");
        }
        // 创建选择器
        String selectorName = generateDataSourceSelectorName(dbKey, "integrates");
        // 整合数据源不进行负载均衡
        healthyDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
        illDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
        // 创建所有的数据源到healthyDataSourceSelector
        for (String integrateKey : integrates.keySet()) {
            // 获取节点配置
            MySplitterDataSourceNodeConfig nodeConfig = integrates.get(integrateKey);
            // 创建包装类对象
            DataSourceWrapper wrapper = new DataSourceWrapper(integrateKey, dbKey, nodeConfig, null);
            // 放入选择器
            healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
            LOGGER.debug("MySplitter has been registered {} data source node: Database:{}, Node:{}",
                    "integrate", dbKey, integrateKey);
        }
    }

    private void createLoadBalanceSelector(String selectorName, MySplitterLoadBalanceConfig loadBalanceConfig,
                                           Map<String, MySplitterDataSourceNodeConfig> readersOrWriters) {
        if (loadBalanceConfig.isEnabled() && readersOrWriters.size() > 1) {
            if ("polling".equals(loadBalanceConfig.getStrategy())) {
                healthyDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
                illDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
            } else if ("random".equals(loadBalanceConfig.getStrategy())) {
                healthyDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
                illDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
            }
        } else {
            healthyDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
            illDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
        }
    }

    private String generateDataSourceSelectorName(String databaseName, String operation) {
        return databaseName + ":" + operation;
    }

    private void submitDataSourceFailTimeoutTask(final String targetDatabase,
                                                 final String operation,
                                                 final DataSourceWrapper dataSourceWrapper) {
        String time;
        if (dataSourceWrapper.getLoadBalanceConfig() == null
                || StringUtil.isBlank(dataSourceWrapper.getLoadBalanceConfig().getFailTimeout())) {
            time = "20s";
        } else {
            time = dataSourceWrapper.getLoadBalanceConfig().getFailTimeout();
        }
        illDataSourceFailTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                AbstractLoadBalanceSelector<DataSourceWrapper> illSelector =
                        illDataSourceSelectorMap.get(generateDataSourceSelectorName(targetDatabase, "operation"));
                if (illSelector == null) {
                    illSelector = illDataSourceSelectorMap.get(generateDataSourceSelectorName(targetDatabase,
                            "integrates"));
                }
                illSelector.release(dataSourceWrapper);
                healthyDataSourceSelectorMap
                        .get(generateDataSourceSelectorName(targetDatabase, operation))
                        .register(dataSourceWrapper, dataSourceWrapper.getNodeConfig().getWeight());
            }
        }, parseTimePeriod(time), parseTimeTimeUnit(time));
    }

    private Integer parseTimePeriod(String time) {
        return Integer.parseInt(time.substring(0, time.length() - 1));
    }

    private TimeUnit parseTimeTimeUnit(String time) {
        String substring = time.substring(time.length() - 1);
        if ("s".equals(substring.toLowerCase())) {
            return TimeUnit.SECONDS;
        } else if ("m".equals(substring.toLowerCase())) {
            return TimeUnit.MINUTES;
        } else if ("h".equals(substring.toLowerCase())) {
            return TimeUnit.HOURS;
        } else {
            return TimeUnit.SECONDS;
        }
    }

}