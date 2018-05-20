package com.mysplitter;

import com.mysplitter.config.MySplitterDataBaseConfig;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MySplitter数据源管理器，用于获取真实数据源，并执行高可用以及负载均衡等实现
 *
 * @Author: wangbor
 * @Date: 2018/5/18 8:59
 */
class MySplitterDataSourceManager {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDataSourceManager.class);

    private MySplitterDataSource router;

    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    private MySplitterDatabaseManager databaseManager;

    private ScheduledThreadPoolExecutor scheduledHighAvailableChecker;

    MySplitterDataSourceManager(MySplitterDataSource router) {
        this.router = router;
        init();
    }

    private void init() {
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

    private void createDataSources() {
        if (!isInitialized.get()) {
            LOGGER.debug("MySplitterDataSourceManager is creating DataSources.");
            // 获取所有的数据库配置，根据读写创建对应的数据源wrapper
            Map<String, MySplitterDataBaseConfig> dbs = this.router.getMySplitterConfig().getMysplitter()
                    .getDatabases();
            for (String dbKey : dbs.keySet()) {
                MySplitterDataBaseConfig mySplitterDataBaseConfig = dbs.get(dbKey);
                // TODO 根据读和写以及整合数据源，创建wrapper并放入预备数据源map或行动数据源map中，同时根据map创建与之对应的负载均衡选择器
            }
            // 初始化准备激活的数据源wrapper
        }
    }

    private void createHighAvailableChecker() {
        if (!isInitialized.get()) {
            LOGGER.debug("MySplitterDataSourceManager is creating HighAvailableChecker.");
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            scheduledHighAvailableChecker = new ScheduledThreadPoolExecutor(availableProcessors, new DaemonThreadFactory
                    ("mysplitter-ha"));
        }
    }

}
