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

    private Map<String, AbstractLoadBalanceSelector<DataSourceWrapper>> standbyDataSourceSelectorMap =
            new ConcurrentHashMap<String, AbstractLoadBalanceSelector<DataSourceWrapper>>();

    MySplitterDataSourceManager(MySplitterDataSource router) throws Exception {
        this.router = router;
        init();
    }

    private void init() throws Exception {
        if (isInitialized.compareAndSet(false, true)) {
            LOGGER.debug("MySplitterDataSourceManager is initializing.");
            // 创建多数据库管理器
            this.databaseManager = new MySplitterDatabaseManager(this.router);
            // 创建读写解析器
            createReadAndWriteParser();
            // 创建数据源异常提醒
            createDataSourceIllAlerter();
            // 创建数据源节点
            createDataSources();
        }
    }

    public void close() throws Exception {
        release(healthyDataSourceSelectorMap);
        release(illDataSourceSelectorMap);
    }

    private void release(Map<String, AbstractLoadBalanceSelector<DataSourceWrapper>> illDataSourceSelectorMap) throws Exception {
        for (String healthyDataSourceKey : illDataSourceSelectorMap.keySet()) {
            AbstractLoadBalanceSelector<DataSourceWrapper> selector = healthyDataSourceSelectorMap.get
                    (healthyDataSourceKey);
            List<DataSourceWrapper> dataSourceWrappers = selector.listAll();
            for (DataSourceWrapper dataSourceWrapper : dataSourceWrappers) {
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

    private void createDataSources() throws Exception {
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
                    createReadersDataSource(dbKey, readers, loadBalanceConfig);
                }
                if (writers != null && writers.size() > 0) {
                    createWritersDataSource(dbKey, writers, loadBalanceConfig);
                }
            }
        }
        // 打印所有数据源的列表
        LOGGER.debug("healthyDataSourceSelectorMap:{}",
                healthyDataSourceSelectorMap.entrySet());
        LOGGER.debug("illDataSourceSelectorMap    :{}",
                illDataSourceSelectorMap.entrySet());
        LOGGER.debug("standbyDataSourceSelectorMap:{}",
                standbyDataSourceSelectorMap.entrySet());
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
                                         Map<String, MySplitterLoadBalanceConfig> loadBalanceConfig)
            throws Exception {
        // 创建选择器
        String selectorName = generateDataSourceSelectorName(dbKey, "readers");
        // 根据负载均衡设置进行设置
        createLoadBalanceSelector(selectorName, loadBalanceConfig.get("read"), readers);
        // 创建所有的数据源到healthyDataSourceSelector
        for (String readerKey : readers.keySet()) {
            // 获取节点配置
            MySplitterDataSourceNodeConfig nodeConfig = readers.get(readerKey);
            // 创建包装类对象
            DataSourceWrapper wrapper = new DataSourceWrapper(readerKey, dbKey, nodeConfig);
            // 放入选择器
            healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
            LOGGER.debug("MySplitter has been registered {} data source node: Database:{}, Node:{}",
                    "read", dbKey, readerKey);
        }
    }

    private void createWritersDataSource(String dbKey,
                                         Map<String, MySplitterDataSourceNodeConfig> writers,
                                         Map<String, MySplitterLoadBalanceConfig> loadBalanceConfig)
            throws Exception {
        // 创建选择器
        String selectorName = generateDataSourceSelectorName(dbKey, "writers");
        // 根据负载均衡设置进行设置
        createLoadBalanceSelector(selectorName, loadBalanceConfig.get("write"), writers);
        // 创建所有的数据源到healthyDataSourceSelector
        for (String writerKey : writers.keySet()) {
            // 获取节点配置
            MySplitterDataSourceNodeConfig nodeConfig = writers.get(writerKey);
            // 创建包装类对象
            DataSourceWrapper wrapper = new DataSourceWrapper(writerKey, dbKey, nodeConfig);
            // 放入选择器
            healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
            LOGGER.debug("MySplitter has been registered {} data source node: Database:{}, Node:{}",
                    "write", dbKey, writerKey);
        }
    }

    private void createIntegratesDataSource(String dbKey,
                                            Map<String, MySplitterDataSourceNodeConfig> integrates)
            throws Exception {
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
        standbyDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
        // 创建所有的数据源到healthyDataSourceSelector
        for (String integrateKey : integrates.keySet()) {
            // 获取节点配置
            MySplitterDataSourceNodeConfig nodeConfig = integrates.get(integrateKey);
            // 创建包装类对象
            DataSourceWrapper wrapper = new DataSourceWrapper(integrateKey, dbKey, nodeConfig);
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
                standbyDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
            } else if ("random".equals(loadBalanceConfig.getStrategy())) {
                healthyDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
                illDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
                standbyDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
            }
        } else {
            healthyDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
            illDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
            standbyDataSourceSelectorMap.put(selectorName, new NoLoadBalanceSelector<DataSourceWrapper>());
        }
    }

    private Integer parseHeartbeatRatePeriod(String heartbeatRate) {
        return Integer.parseInt(heartbeatRate.substring(0, heartbeatRate.length() - 1));
    }

    private TimeUnit parseHeartbeatRateTimeUnit(String heartbeatRate) {
        String substring = heartbeatRate.substring(heartbeatRate.length() - 1);
        if ("s".equals(substring)) {
            return TimeUnit.SECONDS;
        } else if ("m".equals(substring)) {
            return TimeUnit.MINUTES;
        } else {
            return TimeUnit.HOURS;
        }
    }

    private String generateDataSourceSelectorName(String databaseName, String operation) {
        return databaseName + ":" + operation;
    }

    public MySplitterConnectionProxy getConnectionProxy() {
        return new MySplitterConnectionProxy(this);
    }

    public MySplitterConnectionProxy getConnectionProxy(String username, String password) {
        return new MySplitterConnectionProxy(this, username, password);
    }

    public Connection getConnection(MySplitterSqlWrapper sql) throws SQLException {
        return getConnection(sql, null, null);
    }

    public Connection getConnection(MySplitterSqlWrapper sql, String username, String password) throws SQLException {
        String targetDatabase = this.databaseManager.routerHandler(sql.getSql());
        String rewriteSql = this.databaseManager.rewriteSql(sql.getSql());
        if (rewriteSql != null) {
            sql.setSql(rewriteSql);
        }
        String operation = this.readAndWriteParser.parseOperation(sql.getSql());
        AbstractLoadBalanceSelector<DataSourceWrapper> healthySelector =
                this.healthyDataSourceSelectorMap.get(generateDataSourceSelectorName(targetDatabase, operation));
        if (healthySelector == null) {
            healthySelector = this.healthyDataSourceSelectorMap.get(generateDataSourceSelectorName(targetDatabase,
                    "integrates"));
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
        } catch (SQLException | NoHealthyDataSourceException e) {
            // 如果获取连接出现异常，将当前连接放入生病数据源
            if (dataSourceWrapper != null) {
                // 将当前生病的数据源移除
                healthySelector.release(dataSourceWrapper);
                // 将生病的数据源放入生病的map中
                AbstractLoadBalanceSelector<DataSourceWrapper> illSelector =
                        this.illDataSourceSelectorMap.get(generateDataSourceSelectorName(targetDatabase, operation));
                illSelector.register(dataSourceWrapper,
                        dataSourceWrapper.getMySplitterDataSourceNodeConfig().getWeight());
            }
            // TODO 数据源的生成到底由谁来进行控制？是获取连接时控制，还是由定时任务控制？高可用切换时机是否需要考虑？
            // TODO 判断是否还有数据源，如果数据源没有了，激活待命数据源
            // TODO catch 到 NoHealthyDataSourceException 抛出异常等待恢复
        }
        return realConnection;
    }

    public Connection getDefaultConnection() throws SQLException {
        return this.healthyDataSourceSelectorMap
                .get(new ArrayList<>(this.healthyDataSourceSelectorMap.keySet()).get(0))
                .acquire()
                .getRealDataSource()
                .getConnection();
    }
}