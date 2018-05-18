package com.mysplitter;

import com.mysplitter.advise.MySplitterDatabasesRoutingHandlerAdvise;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDatasourceNodeConfig;
import com.mysplitter.warpper.DataSourceWarpper;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * MySplitter数据源管理器，用于获取真实数据源，并执行高可用以及负载均衡等实现
 *
 * @Author: wangbor
 * @Date: 2018/5/18 8:59
 */
class MySplitterDatasourceManager {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MySplitterDatasourceManager.class);

    private MySplitterDataSource router;

    MySplitterDatasourceManager(MySplitterDataSource router) {
        this.router = router;
    }

    private MySplitterDatabasesRoutingHandlerAdvise databasesRoutingHandler;

    private ScheduledThreadPoolExecutor scheduledHighAvailableChecker;

    private Map<String, MySplitterDatasourceNodeConfig> standByDatasourceMap =
            new ConcurrentHashMap<String, MySplitterDatasourceNodeConfig>();

    private Map<String, DataSourceWarpper> healthyDatasourceMap = new ConcurrentHashMap<String, DataSourceWarpper>();

    private Map<String, DataSourceWarpper> illDatasourceMap = new ConcurrentHashMap<String, DataSourceWarpper>();

    void init() {
        LOGGER.debug("MySplitterDatasourceManager is initializing.");
        // 如果有多个数据库，启动多数据库路由，否则不启动
        createDatabasesRoutingHandler();
        // 创建高可用检查线程池
        createHighAvailableChecker();
        // 获取所有的datasource根据高可用心跳频率创建对应的对象以及定时任务，如果不启动心跳，则放入standByDatasourceMap
        createDataSources();
    }

    private void createDataSources() {
        LOGGER.debug("MySplitterDatasourceManager is creating DataSources.");
        // 获取所有的数据库配置
        Map<String, MySplitterDataBaseConfig> dbs = this.router.getMySplitterConfig().getMysplitter().getDatabases();
        for (String dbKey : dbs.keySet()) {
            MySplitterDataBaseConfig mySplitterDataBaseConfig = dbs.get(dbKey);
            // 获取节点的配置
            if (mySplitterDataBaseConfig.getIntegrates() != null &&
                    mySplitterDataBaseConfig.getIntegrates().size() > 0) {
                // TODO 如果是未启动负载均衡，取map中key为default的节点，或第一个节点，放入健康的map，其他的放入备用节点
                // TODO 如果是整合节点，不启动负载均衡
            } else {
                // TODO 如果是未启动负载均衡，取map中key为default的节点，或第一个节点，放入健康的map，其他的放入备用节点
                // TODO 如果是读写分离节点，根据配置使用负载均衡，创建与之对应的负载均衡选择器
            }
        }
    }

    private void createHighAvailableChecker() {
        LOGGER.debug("MySplitterDatasourceManager is creating HighAvailableChecker.");
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        scheduledHighAvailableChecker =
                new ScheduledThreadPoolExecutor(availableProcessors, new DaemonThreadFactory("mysplitter-ha"));
    }

    private void createDatabasesRoutingHandler() {
        Map<String, MySplitterDataBaseConfig> dbs = this.router.getMySplitterConfig().getMysplitter().getDatabases();
        try {
            LOGGER.debug("MySplitterDatasourceManager find {} database{} in mysplitter.yml.", dbs.size(), dbs.size()
                    > 1 ? "s" : "");
            if (dbs.size() > 1) {
                LOGGER.debug("MySplitterDatasourceManager is activating DatabasesRoutingHandler.");
                String routerClz = this.router.getMySplitterConfig().getMysplitter().getDatabasesRoutingHandler();
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Class aClass = classLoader.loadClass(routerClz);
                Constructor constructor = aClass.getConstructor();
                databasesRoutingHandler = (MySplitterDatabasesRoutingHandlerAdvise) constructor.newInstance();
            }
        } catch (Exception e) {
            LOGGER.debug("MySplitterDatasourceManager activated DatabasesRoutingHandler failed!", e);
        }
    }

}
