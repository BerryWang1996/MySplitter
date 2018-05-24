package com.mysplitter;

import com.mysplitter.advise.MySplitterDataSourceIllAlerterAdvise;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterHighAvailableConfig;
import com.mysplitter.config.MySplitterLoadBalanceConfig;
import com.mysplitter.selector.AbstractLoadBalanceSelector;
import com.mysplitter.selector.NoneLoadBalanceSelector;
import com.mysplitter.selector.RandomLoadBalanceSelector;
import com.mysplitter.selector.RoundRobinLoadBalanceSelector;
import com.mysplitter.util.ClassLoaderUtil;
import com.mysplitter.wrapper.DataSourceWrapper;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MySplitter数据源管理器，用于获取真实数据源，并执行高可用以及负载均衡等实现
 *
 * @Author: wangbor
 * @Date: 2018/5/18 8:59
 */
public class MySplitterDataSourceManager {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDataSourceManager.class);

    private static final Integer DEFAULT_INITIAL_DELAY = 20;

    private MySplitterDataSource router;

    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    private MySplitterDatabaseManager databaseManager;

    private ScheduledThreadPoolExecutor scheduledHighAvailableChecker;

    private Map<String, AbstractLoadBalanceSelector<DataSourceWrapper>> healthyDataSourceSelectorMap =
            new ConcurrentHashMap<String, AbstractLoadBalanceSelector<DataSourceWrapper>>();

    private Map<String, AbstractLoadBalanceSelector<DataSourceWrapper>> illDataSourceSelectorMap =
            new ConcurrentHashMap<String, AbstractLoadBalanceSelector<DataSourceWrapper>>();

    private Map<String, AbstractLoadBalanceSelector<DataSourceWrapper>> standbyDataSourceSelectorMap =
            new ConcurrentHashMap<String, AbstractLoadBalanceSelector<DataSourceWrapper>>();

    private Map<String, MySplitterDataSourceIllAlerterAdvise> dataSourceIllAlerterAdviseMap =
            new ConcurrentHashMap<String, MySplitterDataSourceIllAlerterAdvise>();

    MySplitterDataSourceManager(MySplitterDataSource router) throws Exception {
        this.router = router;
        init();
    }

    private void init() throws Exception {
        if (isInitialized.compareAndSet(false, true)) {
            LOGGER.debug("MySplitterDataSourceManager is initializing.");
            // 创建多数据库管理器
            databaseManager = new MySplitterDatabaseManager(this.router);
            // 创建高可用检查线程池
            createHighAvailableChecker();
            // 创建数据源节点
            createDataSources();
        }
    }

    public void close() throws Exception {
        for (String healthyDataSourceKey : healthyDataSourceSelectorMap.keySet()) {
            AbstractLoadBalanceSelector<DataSourceWrapper> selector = healthyDataSourceSelectorMap.get
                    (healthyDataSourceKey);
            List<DataSourceWrapper> dataSourceWrappers = selector.listAll();
            for (DataSourceWrapper dataSourceWrapper : dataSourceWrappers) {
                dataSourceWrapper.releaseRealDataSource();
            }
        }
        for (String healthyDataSourceKey : illDataSourceSelectorMap.keySet()) {
            AbstractLoadBalanceSelector<DataSourceWrapper> selector = healthyDataSourceSelectorMap.get
                    (healthyDataSourceKey);
            List<DataSourceWrapper> dataSourceWrappers = selector.listAll();
            for (DataSourceWrapper dataSourceWrapper : dataSourceWrappers) {
                dataSourceWrapper.releaseRealDataSource();
            }
        }
    }

    private void createHighAvailableChecker() {
        LOGGER.debug("MySplitterDataSourceManager is creating HighAvailableChecker.");
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        scheduledHighAvailableChecker = new ScheduledThreadPoolExecutor(availableProcessors, new DaemonThreadFactory
                ("mysplitter-ha"));
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
            // 获取数据库高可用配置
            Map<String, MySplitterHighAvailableConfig> highAvailableConfig = dataBaseConfig.getHighAvailable();
            // 获取数据源并进行初始化
            Map<String, MySplitterDataSourceNodeConfig> integrates = dataBaseConfig.getIntegrates();
            if (integrates != null && integrates.size() > 0) {
                // 如果整合数据源存在
                createIntegratesDataSource(dbKey, integrates, highAvailableConfig);
            } else {
                Map<String, MySplitterDataSourceNodeConfig> readers = dataBaseConfig.getReaders();
                Map<String, MySplitterDataSourceNodeConfig> writers = dataBaseConfig.getWriters();
                // 如果整合数据源不存在，读取读和写数据源
                if (readers != null && readers.size() > 0) {
                    createReadersDataSource(dbKey, readers, loadBalanceConfig, highAvailableConfig);
                }
                if (writers != null && writers.size() > 0) {
                    createWritersDataSource(dbKey, writers, loadBalanceConfig, highAvailableConfig);
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
                                         Map<String, MySplitterLoadBalanceConfig> loadBalanceConfig,
                                         Map<String, MySplitterHighAvailableConfig> highAvailableConfig)
            throws Exception {
        // 创建选择器
        String selectorName = generateDataSourceSelectorName(dbKey, "readrs");
        // 根据负载均衡设置进行设置
        MySplitterLoadBalanceConfig readLoadBalanceConfig = loadBalanceConfig.get("read");
        if (readLoadBalanceConfig.isEnabled() && readers.size() > 1) {
            if ("polling".equals(readLoadBalanceConfig.getStrategy())) {
                healthyDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
                illDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
                standbyDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
            } else if ("random".equals(readLoadBalanceConfig.getStrategy())) {
                healthyDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
                illDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
                standbyDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
            }
        } else {
            healthyDataSourceSelectorMap.put(selectorName, new NoneLoadBalanceSelector<DataSourceWrapper>());
            illDataSourceSelectorMap.put(selectorName, new NoneLoadBalanceSelector<DataSourceWrapper>());
            standbyDataSourceSelectorMap.put(selectorName, new NoneLoadBalanceSelector<DataSourceWrapper>());
        }
        // 获取整合数据源的配置
        MySplitterHighAvailableConfig readHaConfig = highAvailableConfig.get("read");
        // 创建数据源异常处理器
        String illAlertHandler = readHaConfig.getIllAlertHandler();
        MySplitterDataSourceIllAlerterAdvise instance =
                ClassLoaderUtil.getInstance(illAlertHandler, MySplitterDataSourceIllAlerterAdvise.class);
        dataSourceIllAlerterAdviseMap.put(selectorName, instance);
        // 如果高可用启动，懒加载不启动，创建所有的数据源到healthyDataSourceSelector
        if (readHaConfig.isEnabled() && !readHaConfig.isLazyLoad()) {
            for (String readrKey : readers.keySet()) {
                // 获取节点配置
                MySplitterDataSourceNodeConfig nodeConfig = readers.get(readrKey);
                // 创建包装类对象
                DataSourceWrapper wrapper = new DataSourceWrapper(readrKey, dbKey, nodeConfig);
                // 放入选择器
                healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
            }
            // 提交健康数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [healthy] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "read", readHaConfig.getDetectionSql(), readHaConfig
                    .getHealthyHeartbeatRate());
            submitHealthyDataSourceChecker(selectorName,
                    readHaConfig.getDetectionSql(),
                    readHaConfig.getHealthyHeartbeatRate());
            // 提交异常数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [  ill  ] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "read", readHaConfig.getDetectionSql(), readHaConfig
                    .getIllHeartbeatRate());
            submitIllDataSourceChecker(selectorName,
                    readHaConfig.getDetectionSql(),
                    readHaConfig.getIllHeartbeatRate());
        }
        // 如果高可用启动，懒加载启动，将第一个数据源放入healthyDataSourceSelector，其他放入standbyDataSourceSelectorMap，然后创建检查线程
        else if (readHaConfig.isEnabled() && readHaConfig.isLazyLoad()) {
            boolean isFirst = true;
            for (String readrKey : readers.keySet()) {
                if (isFirst) {
                    // 将第一个数据源放入健康数据源列表
                    isFirst = false;
                    // 获取节点配置
                    MySplitterDataSourceNodeConfig nodeConfig = readers.get(readrKey);
                    // 创建包装类对象
                    DataSourceWrapper wrapper = new DataSourceWrapper(readrKey, dbKey, nodeConfig);
                    // 放入选择器
                    healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
                } else {
                    // 其他放入standby
                    // 获取节点配置
                    MySplitterDataSourceNodeConfig nodeConfig = readers.get(readrKey);
                    // 创建包装类对象
                    DataSourceWrapper wrapper = new DataSourceWrapper(readrKey, dbKey, nodeConfig);
                    // 放入选择器
                    standbyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
                }
            }
            // 提交健康数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [healthy] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "read", readHaConfig.getDetectionSql(), readHaConfig
                    .getHealthyHeartbeatRate());
            submitHealthyDataSourceChecker(selectorName,
                    readHaConfig.getDetectionSql(),
                    readHaConfig.getHealthyHeartbeatRate());
            // 提交异常数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [  ill  ] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "read", readHaConfig.getDetectionSql(), readHaConfig
                    .getIllHeartbeatRate());
            submitIllDataSourceChecker(selectorName,
                    readHaConfig.getDetectionSql(),
                    readHaConfig.getIllHeartbeatRate());
        }
        // 如果高可用不启动，懒加载无论是否启动，将第一个数据源放入healthyDataSourceSelector，其他忽略，然后不创建检查线程
        else if (!readHaConfig.isEnabled()) {
            boolean isFirst = true;
            for (String readrKey : readers.keySet()) {
                if (isFirst) {
                    // 将第一个数据源放入健康数据源列表
                    isFirst = false;
                    // 获取节点配置
                    MySplitterDataSourceNodeConfig nodeConfig = readers.get(readrKey);
                    // 创建包装类对象
                    DataSourceWrapper wrapper = new DataSourceWrapper(readrKey, dbKey, nodeConfig);
                    // 放入选择器
                    healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
                } else {
                    LOGGER.warn("MySplitter found multiple {} data source node in database {}. Because of " +
                                    "highAvailable is disabled, MySplitter will ignore data source node {}",
                            "read",
                            dbKey, readrKey);
                }
            }
        }
    }

    private void createWritersDataSource(String dbKey,
                                         Map<String, MySplitterDataSourceNodeConfig> writers,
                                         Map<String, MySplitterLoadBalanceConfig> loadBalanceConfig,
                                         Map<String, MySplitterHighAvailableConfig> highAvailableConfig)
            throws Exception {
        // 创建选择器
        String selectorName = generateDataSourceSelectorName(dbKey, "writers");
        // 根据负载均衡设置进行设置
        MySplitterLoadBalanceConfig readLoadBalanceConfig = loadBalanceConfig.get("write");
        if (readLoadBalanceConfig.isEnabled() && writers.size() > 1) {
            if ("polling".equals(readLoadBalanceConfig.getStrategy())) {
                healthyDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
                illDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
                standbyDataSourceSelectorMap.put(selectorName, new RoundRobinLoadBalanceSelector<DataSourceWrapper>());
            } else if ("random".equals(readLoadBalanceConfig.getStrategy())) {
                healthyDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
                illDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
                standbyDataSourceSelectorMap.put(selectorName, new RandomLoadBalanceSelector<DataSourceWrapper>());
            }
        } else {
            healthyDataSourceSelectorMap.put(selectorName, new NoneLoadBalanceSelector<DataSourceWrapper>());
            illDataSourceSelectorMap.put(selectorName, new NoneLoadBalanceSelector<DataSourceWrapper>());
            standbyDataSourceSelectorMap.put(selectorName, new NoneLoadBalanceSelector<DataSourceWrapper>());
        }
        // 获取整合数据源的配置
        MySplitterHighAvailableConfig writeHaConfig = highAvailableConfig.get("write");
        // 创建数据源异常处理器
        String illAlertHandler = writeHaConfig.getIllAlertHandler();
        MySplitterDataSourceIllAlerterAdvise instance =
                ClassLoaderUtil.getInstance(illAlertHandler, MySplitterDataSourceIllAlerterAdvise.class);
        dataSourceIllAlerterAdviseMap.put(selectorName, instance);
        // 如果高可用启动，懒加载不启动，创建所有的数据源到healthyDataSourceSelector
        if (writeHaConfig.isEnabled() && !writeHaConfig.isLazyLoad()) {
            for (String writerKey : writers.keySet()) {
                // 获取节点配置
                MySplitterDataSourceNodeConfig nodeConfig = writers.get(writerKey);
                // 创建包装类对象
                DataSourceWrapper wrapper = new DataSourceWrapper(writerKey, dbKey, nodeConfig);
                // 放入选择器
                healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
            }
            // 提交健康数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [healthy] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "write", writeHaConfig.getDetectionSql(), writeHaConfig
                    .getHealthyHeartbeatRate());
            submitHealthyDataSourceChecker(selectorName,
                    writeHaConfig.getDetectionSql(),
                    writeHaConfig.getHealthyHeartbeatRate());
            // 提交异常数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [  ill  ] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "write", writeHaConfig.getDetectionSql(), writeHaConfig
                    .getIllHeartbeatRate());
            submitIllDataSourceChecker(selectorName,
                    writeHaConfig.getDetectionSql(),
                    writeHaConfig.getIllHeartbeatRate());
        }
        // 如果高可用启动，懒加载启动，将第一个数据源放入healthyDataSourceSelector，其他放入standbyDataSourceSelectorMap，然后创建检查线程
        else if (writeHaConfig.isEnabled() && writeHaConfig.isLazyLoad()) {
            boolean isFirst = true;
            for (String writerKey : writers.keySet()) {
                if (isFirst) {
                    // 将第一个数据源放入健康数据源列表
                    isFirst = false;
                    // 获取节点配置
                    MySplitterDataSourceNodeConfig nodeConfig = writers.get(writerKey);
                    // 创建包装类对象
                    DataSourceWrapper wrapper = new DataSourceWrapper(writerKey, dbKey, nodeConfig);
                    // 放入选择器
                    healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
                } else {
                    // 其他放入standby
                    // 获取节点配置
                    MySplitterDataSourceNodeConfig nodeConfig = writers.get(writerKey);
                    // 创建包装类对象
                    DataSourceWrapper wrapper = new DataSourceWrapper(writerKey, dbKey, nodeConfig);
                    // 放入选择器
                    standbyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
                }
            }
            // 提交健康数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [healthy] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "write", writeHaConfig.getDetectionSql(), writeHaConfig
                    .getHealthyHeartbeatRate());
            submitHealthyDataSourceChecker(selectorName,
                    writeHaConfig.getDetectionSql(),
                    writeHaConfig.getHealthyHeartbeatRate());
            // 提交异常数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [  ill  ] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "write", writeHaConfig.getDetectionSql(), writeHaConfig
                    .getIllHeartbeatRate());
            submitIllDataSourceChecker(selectorName,
                    writeHaConfig.getDetectionSql(),
                    writeHaConfig.getIllHeartbeatRate());
        }
        // 如果高可用不启动，懒加载无论是否启动，将第一个数据源放入healthyDataSourceSelector，其他忽略，然后不创建检查线程
        else if (!writeHaConfig.isEnabled()) {
            boolean isFirst = true;
            for (String writerKey : writers.keySet()) {
                if (isFirst) {
                    // 将第一个数据源放入健康数据源列表
                    isFirst = false;
                    // 获取节点配置
                    MySplitterDataSourceNodeConfig nodeConfig = writers.get(writerKey);
                    // 创建包装类对象
                    DataSourceWrapper wrapper = new DataSourceWrapper(writerKey, dbKey, nodeConfig);
                    // 放入选择器
                    healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
                } else {
                    LOGGER.warn("MySplitter found multiple {} data source node in database {}. Because of " +
                                    "highAvailable is disabled, MySplitter will ignore data source node {}",
                            "write",
                            dbKey, writerKey);
                }
            }
        }
    }

    private void createIntegratesDataSource(String dbKey,
                                            Map<String, MySplitterDataSourceNodeConfig> integrates,
                                            Map<String, MySplitterHighAvailableConfig> highAvailableConfig)
            throws Exception {
        // 创建选择器
        String selectorName = generateDataSourceSelectorName(dbKey, "integrates");
        // 整合数据源不进行负载均衡
        healthyDataSourceSelectorMap.put(selectorName, new NoneLoadBalanceSelector<DataSourceWrapper>());
        illDataSourceSelectorMap.put(selectorName, new NoneLoadBalanceSelector<DataSourceWrapper>());
        standbyDataSourceSelectorMap.put(selectorName, new NoneLoadBalanceSelector<DataSourceWrapper>());
        // 获取整合数据源的配置
        MySplitterHighAvailableConfig integrateHaConfig = highAvailableConfig.get("integrate");
        // 创建数据源异常处理器
        String illAlertHandler = integrateHaConfig.getIllAlertHandler();
        MySplitterDataSourceIllAlerterAdvise instance =
                ClassLoaderUtil.getInstance(illAlertHandler, MySplitterDataSourceIllAlerterAdvise.class);
        dataSourceIllAlerterAdviseMap.put(selectorName, instance);
        // 如果高可用启动，懒加载不启动，创建所有的数据源到healthyDataSourceSelector
        if (integrateHaConfig.isEnabled() && !integrateHaConfig.isLazyLoad()) {
            for (String integrateKey : integrates.keySet()) {
                // 获取节点配置
                MySplitterDataSourceNodeConfig nodeConfig = integrates.get(integrateKey);
                // 创建包装类对象
                DataSourceWrapper wrapper = new DataSourceWrapper(integrateKey, dbKey, nodeConfig);
                // 放入选择器
                healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
            }
            // 提交健康数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [healthy] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "integrate", integrateHaConfig.getDetectionSql(), integrateHaConfig
                    .getHealthyHeartbeatRate());
            submitHealthyDataSourceChecker(selectorName,
                    integrateHaConfig.getDetectionSql(),
                    integrateHaConfig.getHealthyHeartbeatRate());
            // 提交异常数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [  ill  ] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "integrate", integrateHaConfig.getDetectionSql(), integrateHaConfig
                    .getIllHeartbeatRate());
            submitIllDataSourceChecker(selectorName,
                    integrateHaConfig.getDetectionSql(),
                    integrateHaConfig.getIllHeartbeatRate());
        }
        // 如果高可用启动，懒加载启动，将第一个数据源放入healthyDataSourceSelector，其他放入standbyDataSourceSelectorMap，然后创建检查线程
        else if (integrateHaConfig.isEnabled() && integrateHaConfig.isLazyLoad()) {
            boolean isFirst = true;
            for (String integrateKey : integrates.keySet()) {
                if (isFirst) {
                    // 将第一个数据源放入健康数据源列表
                    isFirst = false;
                    // 获取节点配置
                    MySplitterDataSourceNodeConfig nodeConfig = integrates.get(integrateKey);
                    // 创建包装类对象
                    DataSourceWrapper wrapper = new DataSourceWrapper(integrateKey, dbKey, nodeConfig);
                    // 放入选择器
                    healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
                } else {
                    // 其他放入standby
                    // 获取节点配置
                    MySplitterDataSourceNodeConfig nodeConfig = integrates.get(integrateKey);
                    // 创建包装类对象
                    DataSourceWrapper wrapper = new DataSourceWrapper(integrateKey, dbKey, nodeConfig);
                    // 放入选择器
                    standbyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
                }
            }
            // 提交健康数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [healthy] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "integrate", integrateHaConfig.getDetectionSql(), integrateHaConfig
                    .getHealthyHeartbeatRate());
            submitHealthyDataSourceChecker(selectorName,
                    integrateHaConfig.getDetectionSql(),
                    integrateHaConfig.getHealthyHeartbeatRate());
            // 提交异常数据源检查任务
            LOGGER.debug("MySplitterHighAvailableChecker is submit a [  ill  ] data source schedule at " +
                    "fixed rate task. Database:{}, HighAvailableNodeMode:{}, detectionSql:{}, " +
                    "HeartbeatRate:{}", dbKey, "integrate", integrateHaConfig.getDetectionSql(), integrateHaConfig
                    .getIllHeartbeatRate());
            submitIllDataSourceChecker(selectorName,
                    integrateHaConfig.getDetectionSql(),
                    integrateHaConfig.getIllHeartbeatRate());
        }
        // 如果高可用不启动，懒加载无论是否启动，将第一个数据源放入healthyDataSourceSelector，其他忽略，然后不创建检查线程
        else if (!integrateHaConfig.isEnabled()) {
            boolean isFirst = true;
            for (String integrateKey : integrates.keySet()) {
                if (isFirst) {
                    // 将第一个数据源放入健康数据源列表
                    isFirst = false;
                    // 获取节点配置
                    MySplitterDataSourceNodeConfig nodeConfig = integrates.get(integrateKey);
                    // 创建包装类对象
                    DataSourceWrapper wrapper = new DataSourceWrapper(integrateKey, dbKey, nodeConfig);
                    // 放入选择器
                    healthyDataSourceSelectorMap.get(selectorName).register(wrapper, nodeConfig.getWeight());
                } else {
                    LOGGER.warn("MySplitter found multiple {} data source node in database {}. Because of " +
                                    "highAvailable is disabled, MySplitter will ignore data source node {}",
                            "integrates",
                            dbKey, integrateKey);
                }
            }
        }
    }

    private void submitHealthyDataSourceChecker(final String selectorName,
                                                final String detectionSql,
                                                final String heartbeatRate) {
        // 创建检查线程
        scheduledHighAvailableChecker.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                // 获取数据源，进行检查
                AbstractLoadBalanceSelector<DataSourceWrapper> selector =
                        healthyDataSourceSelectorMap.get(selectorName);
                List<DataSourceWrapper> dataSourceWrappers = selector.listAll();
                for (DataSourceWrapper dataSourceWrapper : dataSourceWrappers) {
                    try {
                        dataSourceWrapper.healthyCheck(detectionSql);
                    } catch (Exception e) {
                        // 如果出现异常提交到异常提醒处理器
                        MySplitterDataSourceIllAlerterAdvise alerter = dataSourceIllAlerterAdviseMap.get(selectorName);
                        alerter.illAlerter(dataSourceWrapper.getDataBaseName(), dataSourceWrapper.getNodeName(), e);
                        // 将当前数据源节点迁移到异常数据源节点
                        selector.release(dataSourceWrapper);
                        illDataSourceSelectorMap.get(selectorName).register(dataSourceWrapper,
                                dataSourceWrapper.getMySplitterDataSourceNodeConfig().getWeight());
                    }
                }
            }
        }, DEFAULT_INITIAL_DELAY, parseHeartbeatRatePeriod(heartbeatRate), parseHeartbeatRateTimeUnit(heartbeatRate));
    }

    private void submitIllDataSourceChecker(final String selectorName,
                                            final String detectionSql,
                                            final String heartbeatRate) {
        // 创建检查线程
        scheduledHighAvailableChecker.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                // 获取数据源，进行检查
                AbstractLoadBalanceSelector<DataSourceWrapper> selector =
                        illDataSourceSelectorMap.get(selectorName);
                List<DataSourceWrapper> dataSourceWrappers = selector.listAll();
                for (DataSourceWrapper dataSourceWrapper : dataSourceWrappers) {
                    boolean isOk = true;
                    try {
                        DataSource realDataSource = dataSourceWrapper.getRealDataSource();
                        Statement statement = realDataSource.getConnection().createStatement();
                        statement.execute(detectionSql);
                    } catch (Exception e) {
                        // 如果出现异常提交到异常提醒处理器
                        MySplitterDataSourceIllAlerterAdvise alerter = dataSourceIllAlerterAdviseMap.get(selectorName);
                        alerter.illAlerter(dataSourceWrapper.getDataBaseName(), dataSourceWrapper.getNodeName(), e);
                        isOk = false;
                    }
                    // 如果没出现异常，将当前节点恢复至正常节点
                    if (isOk) {
                        selector.release(dataSourceWrapper);
                        healthyDataSourceSelectorMap.get(selectorName).register(dataSourceWrapper,
                                dataSourceWrapper.getMySplitterDataSourceNodeConfig().getWeight());
                    }
                }
            }
        }, DEFAULT_INITIAL_DELAY, parseHeartbeatRatePeriod(heartbeatRate), parseHeartbeatRateTimeUnit(heartbeatRate));
    }

    private Integer parseHeartbeatRatePeriod(String heartbeatRate) {
        return Integer.parseInt(heartbeatRate.substring(0, heartbeatRate.length() - 1));
    }

    private TimeUnit parseHeartbeatRateTimeUnit(String heartbeatRate) {
        String substring = heartbeatRate.substring(heartbeatRate.length() - 1);
        if (substring.equals("s")) {
            return TimeUnit.SECONDS;
        } else if (substring.equals("m")) {
            return TimeUnit.MINUTES;
        } else {
            return TimeUnit.HOURS;
        }
    }

    private String generateDataSourceSelectorName(String databaseName, String operation) {
        return databaseName + ":" + operation;
    }

}