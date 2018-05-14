package com.mysplitter.util;

import com.mysplitter.advise.DatasourceDiedAlerterAdvise;
import com.mysplitter.config.*;
import com.mysplitter.exceptions.DataSourceClassNotDefine;
import com.mysplitter.exceptions.DataSourceConfigurationNotDefine;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import javax.naming.OperationNotSupportedException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置文件工具类
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:15
 */
public class ConfigurationUtil {

    private static MySplitterRootConfig mySplitterRootConfig;

    private static String[] supportSwitchOpportunities = {"on-error", "scheduled", "on-error-dissolve"};

    private static String[] supportStrategy = {"polling", "weight"};

    static {
        // 读取配置文件
        final InputStream resource = ConfigurationUtil.class.getClassLoader().getResourceAsStream("mysplitter.yml");
        // 饿汉式加载配置对象
        Yaml yaml = new Yaml(new Constructor() {
            @Override
            public void setAllowDuplicateKeys(boolean allowDuplicateKeys) {
                super.setAllowDuplicateKeys(false);
            }
        }, new Representer(), new DumperOptions(), new LoaderOptions(), new Resolver());
        mySplitterRootConfig = yaml.loadAs(resource, MySplitterRootConfig.class);
    }

    private ConfigurationUtil() {
    }

    public static MySplitterRootConfig getMySplitterConfig() {
        return ConfigurationUtil.mySplitterRootConfig;
    }

    public static void checkMySplitterConfig(MySplitterRootConfig mySplitterRootConfig) throws Exception {
        MySplitterConfig mySplitterConfig = mySplitterRootConfig.getMysplitter();
        // 检查datasourceClass是否缺失，以及是否正确
        String datasourceClass = mySplitterConfig.getCommon().getDatasourceClass();
        if (StringUtil.isBlank(datasourceClass)) {
            Map<String, MySplitterDataBaseConfig> databases = mySplitterConfig.getDatabases();
            for (String databaseKey : databases.keySet()) {
                MySplitterDataBaseConfig mySplitterDataBaseConfig = databases.get(databaseKey);
                if (StringUtil.isBlank(mySplitterDataBaseConfig.getDatasourceClass())) {
                    Map<String, MySplitterIntegrateConfig> integrates = mySplitterDataBaseConfig.getIntegrates();
                    for (String integrateKey : integrates.keySet()) {
                        MySplitterIntegrateConfig mySplitterIntegrateConfig = integrates.get(integrateKey);
                        if (StringUtil.isBlank(mySplitterIntegrateConfig.getDatasourceClass())) {
                            throw new DataSourceClassNotDefine(databaseKey, integrateKey);
                        } else {
                            ConfigurationUtil.isClassNameLegal(mySplitterDataBaseConfig.getDatasourceClass());
                        }
                    }
                    Map<String, MySplitterReaderConfig> readers = mySplitterDataBaseConfig.getReaders();
                    for (String readerKey : readers.keySet()) {
                        MySplitterReaderConfig mySplitterReaderConfig = readers.get(readerKey);
                        if (StringUtil.isBlank(mySplitterReaderConfig.getDatasourceClass())) {
                            throw new DataSourceClassNotDefine(databaseKey, readerKey);
                        } else {
                            ConfigurationUtil.isClassNameLegal(mySplitterDataBaseConfig.getDatasourceClass());
                        }
                    }
                    Map<String, MySplitterWriterConfig> writers = mySplitterDataBaseConfig.getWriters();
                    for (String writerKey : writers.keySet()) {
                        MySplitterWriterConfig mySplitterWriterConfig = writers.get(writerKey);
                        if (StringUtil.isBlank(mySplitterWriterConfig.getDatasourceClass())) {
                            throw new DataSourceClassNotDefine(databaseKey, writerKey);
                        } else {
                            ConfigurationUtil.isClassNameLegal(mySplitterDataBaseConfig.getDatasourceClass());
                        }
                    }
                } else {
                    ConfigurationUtil.isClassNameLegal(mySplitterDataBaseConfig.getDatasourceClass());
                }
            }
        } else {
            ConfigurationUtil.isClassNameLegal(datasourceClass);
        }
        // 检查highAvailable
        MySplitterHighAvailableConfig highAvailable = mySplitterConfig.getCommon().getHighAvailable();
        if (highAvailable == null) {
            highAvailable = new MySplitterHighAvailableConfig();
        }
        // 如果切换时机不在定义的范围内，报错
        String switchOpportunity = highAvailable.getSwitchOpportunity();
        // 设置为小写
        if (StringUtil.isNotBlank(switchOpportunity)) {
            highAvailable.setSwitchOpportunity(highAvailable.getSwitchOpportunity().toLowerCase());
        }
        if (!ConfigurationUtil.isHighAvailableSwitchOpportunityLegal(switchOpportunity)) {
            throw new IllegalArgumentException("HighAvailable switch opportunity not support " +
                    switchOpportunity + "!");
        }
        // 如果不存在默认sql，设置
        String detectionSql = highAvailable.getDetectionSql();
        if (StringUtil.isBlank(detectionSql)) {
            highAvailable.setDetectionSql(MySplitterHighAvailableConfig.DEFAULT_DETECTION_SQL);
        }
        // 如果不存在心跳速度，设置
        if (StringUtil.isBlank(highAvailable.getHeartbeatRate())) {
            highAvailable.setHeartbeatRate(MySplitterHighAvailableConfig.DEFAULT_HEARTBEAT_RATE);
        }
        // 心跳速度全部小写
        highAvailable.setHeartbeatRate(highAvailable.getHeartbeatRate().toLowerCase());
        // 心跳配置不符合规范，报错
        if (!ConfigurationUtil.isHighAvailableHeartbeatRateLegal(highAvailable.getHeartbeatRate())) {
            throw new IllegalArgumentException("HighAvailable heartbeat rate not support " +
                    switchOpportunity + "!");
        }
        // 如果数据源异常警告处理器填写，检查数据源错误警告处理器是否存在
        if (StringUtil.isNotBlank(highAvailable.getDiedAlertHandler())) {
            ConfigurationUtil.isHighAvailableDiedAlertHandlerLegal(highAvailable.getDiedAlertHandler());
        }
        // 检查loadBalance
        Map<String, MySplitterLoadBalanceConfig> loadBalance = mySplitterConfig.getCommon().getLoadBalance();
        if (loadBalance == null) {
            loadBalance = new HashMap<String, MySplitterLoadBalanceConfig>();
        }
        for (String s : loadBalance.keySet()) {
            if (!("read".equals(s) || "write".equals(s))) {
                throw new OperationNotSupportedException("LoadBalance do not support key " + s + "!");
            }
        }
        if (loadBalance.get("read") != null) {
            MySplitterLoadBalanceConfig read = loadBalance.get("read");
            boolean enabled = read.isEnabled();
            if (enabled) {
                // 转换为小写
                if (StringUtil.isNotBlank(read.getStrategy())) {
                    read.setStrategy(read.getStrategy().toLowerCase());
                }
                if (!ConfigurationUtil.isLoadBalanceStrategyLegal(read.getStrategy())) {
                    throw new IllegalArgumentException("LoadBalance strategy not support " +
                            read.getStrategy() + "!");
                }
            }
        } else {
            MySplitterLoadBalanceConfig balanceConfig = new MySplitterLoadBalanceConfig();
            balanceConfig.setEnabled(false);
            loadBalance.put("read", balanceConfig);
        }
        if (loadBalance.get("write") != null) {
            MySplitterLoadBalanceConfig write = loadBalance.get("write");
            boolean enabled = write.isEnabled();
            if (enabled) {
                // 转换为小写
                if (StringUtil.isNotBlank(write.getStrategy())) {
                    write.setStrategy(write.getStrategy().toLowerCase());
                }
                if (!ConfigurationUtil.isLoadBalanceStrategyLegal(write.getStrategy())) {
                    throw new IllegalArgumentException("LoadBalance strategy not support " +
                            write.getStrategy() + "!");
                }
            }
        } else {
            MySplitterLoadBalanceConfig balanceConfig = new MySplitterLoadBalanceConfig();
            balanceConfig.setEnabled(false);
            loadBalance.put("write", balanceConfig);
        }
        // 检查databases
        Map<String, MySplitterDataBaseConfig> databases = mySplitterConfig.getDatabases();
        if (databases.size() == 0) {
            throw new IllegalArgumentException("Databases don't have any configurations!");
        }
        for (String databaseKey : databases.keySet()) {
            MySplitterDataBaseConfig mySplitterDataBaseConfig = databases.get(databaseKey);
            // 如果没有设置reader或者writer，报错
            if (mySplitterDataBaseConfig.getReaders().size() == 0 &&
                    mySplitterDataBaseConfig.getWriters().size() == 0) {
                throw new IllegalArgumentException("Database must contains reader or writer!");
            }
            // 如果没有对数据源进行配置，报错
            if (mySplitterDataBaseConfig.getReaders().size() > 0) {
                Map<String, MySplitterReaderConfig> readers = mySplitterDataBaseConfig.getReaders();
                for (String readerKey : readers.keySet()) {
                    MySplitterReaderConfig mySplitterReaderConfig = readers.get(readerKey);
                    if (mySplitterReaderConfig.getConfiguration() == null ||
                            mySplitterReaderConfig.getConfiguration().size() == 0) {
                        throw new DataSourceConfigurationNotDefine(databaseKey, readerKey);
                    }
                }
            } else {
                Map<String, MySplitterWriterConfig> writers = mySplitterDataBaseConfig.getWriters();
                for (String writerKey : writers.keySet()) {
                    MySplitterWriterConfig mySplitterWriterConfig = writers.get(writerKey);
                    if (mySplitterWriterConfig.getConfiguration() == null ||
                            mySplitterWriterConfig.getConfiguration().size() == 0) {
                        throw new DataSourceConfigurationNotDefine(databaseKey, writerKey);
                    }
                }
            }
        }
        // 检查log
        if (mySplitterConfig.getLog() == null) {
            mySplitterConfig.setLog(new MySplitterLogConfig());
        }
    }

    private static boolean isHighAvailableSwitchOpportunityLegal(String switchOpportunity) {
        if (StringUtil.isBlank(switchOpportunity)) {
            return false;
        }
        String[] strings = supportSwitchOpportunities;
        for (String string : strings) {
            if (string.equals(switchOpportunity)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHighAvailableHeartbeatRateLegal(String heartbeatRate) {
        if (heartbeatRate.endsWith("s") || heartbeatRate.endsWith("m") || heartbeatRate.endsWith("h")) {
            if ((heartbeatRate.length() - 1) > 0) {
                String substring = heartbeatRate.substring(0, heartbeatRate.length() - 1);
                try {
                    return Integer.parseInt(substring) >= 0;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return false;
    }

    private static boolean isHighAvailableDiedAlertHandlerLegal(String diedAlertHandler) throws ClassNotFoundException {
        Class<?> aClass = Class.forName(diedAlertHandler);
        Class<?>[] interfaces = aClass.getInterfaces();
        for (Class<?> anInterface : interfaces) {
            if (anInterface.getName().equals(DatasourceDiedAlerterAdvise.class.getName())) {
                return true;
            }
        }
        throw new IllegalArgumentException("HighAvailable died alert handler not support " +
                diedAlertHandler + ", may not implements com.mysplitter.advise" +
                ".DatasourceDiedAlerterAdvise!");
    }

    private static boolean isLoadBalanceStrategyLegal(String strategy) {
        if (StringUtil.isBlank(strategy)) {
            return false;
        }
        List<String> strings = Arrays.asList(supportStrategy);
        return strings.contains(strategy);
    }

    private static void isClassNameLegal(String datasourceClass) throws ClassNotFoundException {
        Thread.currentThread().getContextClassLoader().loadClass(datasourceClass);
    }

}
