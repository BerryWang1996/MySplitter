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

package com.mysplitter.util;

import com.mysplitter.advise.MySplitterDataSourceIllAlerterAdvise;
import com.mysplitter.advise.MySplitterDatabasesRoutingHandlerAdvise;
import com.mysplitter.advise.MySplitterFilterAdvise;
import com.mysplitter.advise.MySplitterReadAndWriteParserAdvise;
import com.mysplitter.config.*;
import com.mysplitter.exceptions.DataSourceClassNotDefine;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置文件工具类
 */
public class ConfigurationUtil {

    private static final List<String> SUPPORT_SWITCH_OPPORTUNITIES_LIST =
            Arrays.asList("on-error", "scheduled", "on-error-dissolve");

    private static final List<String> SUPPORT_STRATEGY_LIST =
            Arrays.asList("polling", "random");

    private static final List<String> SUPPORT_HA_NODE_MODE_LIST =
            Arrays.asList("integrate", "read", "write");

    private static final List<String> SUPPORT_LB_NODE_MODE_LIST =
            Arrays.asList("read", "write");

    private ConfigurationUtil() {
    }

    public static MySplitterRootConfig getMySplitterConfig(String fileName) {
        // 读取配置文件
        final InputStream resource = ConfigurationUtil.class.getClassLoader().getResourceAsStream(fileName);
        // 饿汉式加载配置对象
        Yaml yaml = new Yaml(new Constructor() {
            @Override
            public void setAllowDuplicateKeys(boolean allowDuplicateKeys) {
                super.setAllowDuplicateKeys(false);
            }
        }, new Representer(), new DumperOptions(), new LoaderOptions(), new Resolver());
        return yaml.loadAs(resource, MySplitterRootConfig.class);
    }

    public static void checkMySplitterConfig(MySplitterRootConfig mySplitterRootConfig) throws Exception {
        MySplitterConfig mySplitterConfig = mySplitterRootConfig.getMysplitter();
        Map<String, MySplitterDataBaseConfig> databases = mySplitterConfig.getDatabases();
        // 检查filters
        List<String> filters = mySplitterConfig.getFilters();
        if (filters != null && filters.size() > 0) {
            for (String filter : filters) {
                isFilterLegal(filter);
            }
        }
        // 检查highAvailable
        Map<String, MySplitterHighAvailableConfig> commonHaConfigMap = mySplitterConfig.getCommon().getHighAvailable();
        if (commonHaConfigMap == null || commonHaConfigMap.size() == 0) {
            // 如果common的highAvailable是空，就设置关闭，以便于子节点获取
            commonHaConfigMap = new ConcurrentHashMap<String, MySplitterHighAvailableConfig>(1);
            for (String supportHaKey : SUPPORT_HA_NODE_MODE_LIST) {
                MySplitterHighAvailableConfig mySplitterHighAvailableConfig = new MySplitterHighAvailableConfig();
                mySplitterHighAvailableConfig.setEnabled(false);
                mySplitterHighAvailableConfig.setIllAlertHandler("com.mysplitter.DefaultDataSourceIllAlertHandler");
                commonHaConfigMap.put(supportHaKey, mySplitterHighAvailableConfig);
            }
        } else {
            // 如果common的highAvailable有填写一项或多项，补充空项，然后检查highAvailable key是否在允许的范围内
            for (String supportHaKey : SUPPORT_HA_NODE_MODE_LIST) {
                if (commonHaConfigMap.get(supportHaKey) == null) {
                    MySplitterHighAvailableConfig mySplitterHighAvailableConfig = new MySplitterHighAvailableConfig();
                    mySplitterHighAvailableConfig.setEnabled(false);
                    mySplitterHighAvailableConfig.setIllAlertHandler("com.mysplitter.DefaultDataSourceIllAlertHandler");
                    commonHaConfigMap.put(supportHaKey, mySplitterHighAvailableConfig);
                }
                // 如果用户没设置数据源异常处理器，设置为默认的数据源异常处理器
                if (StringUtil.isBlank(commonHaConfigMap.get(supportHaKey).getIllAlertHandler())) {
                    commonHaConfigMap.get(supportHaKey).setIllAlertHandler("com.mysplitter" +
                            ".DefaultDataSourceIllAlertHandler");
                }
            }
            isHighAvailableMapLegal(commonHaConfigMap);
        }
        // 查看每个dataSource是否配置highAvailable
        for (String databaseKey : databases.keySet()) {
            MySplitterDataBaseConfig mySplitterDataBaseConfig = databases.get(databaseKey);
            if (mySplitterDataBaseConfig.getHighAvailable() != null &&
                    mySplitterDataBaseConfig.getHighAvailable().size() > 0) {
                // 如果配置，检查highAvailable key是否在允许的范围内
                isHighAvailableMapLegal(mySplitterDataBaseConfig.getHighAvailable());
            } else {
                // 如果没有配置，使用common的配置
                mySplitterDataBaseConfig.setHighAvailable(commonHaConfigMap);
            }
        }
        // 检查loadBalance
        Map<String, MySplitterLoadBalanceConfig> commonLoadBalance = mySplitterConfig.getCommon().getLoadBalance();
        if (commonLoadBalance == null || commonLoadBalance.size() == 0) {
            // 如果common的loadBalance是空，就设置read和write关闭，以便于子节点获取
            Map<String, MySplitterLoadBalanceConfig> loadBalanceMap =
                    new ConcurrentHashMap<String, MySplitterLoadBalanceConfig>();
            // 创建一个关闭负载均衡的对象
            MySplitterLoadBalanceConfig closedLoadBalanceConfig = new MySplitterLoadBalanceConfig();
            closedLoadBalanceConfig.setEnabled(false);
            loadBalanceMap.put("read", closedLoadBalanceConfig);
            loadBalanceMap.put("write", closedLoadBalanceConfig);
            mySplitterConfig.getCommon().setLoadBalance(loadBalanceMap);
        } else {
            // 如果common的loadBalance有填写一项或多项，补充空项，然后检查key是否在允许的范围内，配置是否正确
            for (String supportLbKey : SUPPORT_LB_NODE_MODE_LIST) {
                if (commonLoadBalance.get(supportLbKey) == null) {
                    MySplitterLoadBalanceConfig mySplitterLoadBalanceConfig = new MySplitterLoadBalanceConfig();
                    mySplitterLoadBalanceConfig.setEnabled(false);
                    commonLoadBalance.put(supportLbKey, mySplitterLoadBalanceConfig);
                }
            }
            isLoadBalanceMapLegal(commonLoadBalance);
            mySplitterConfig.getCommon().setLoadBalance(commonLoadBalance);
        }
        // 查看每个dataSource是否配置loadBalance
        for (String databaseKey : databases.keySet()) {
            MySplitterDataBaseConfig mySplitterDataBaseConfig = databases.get(databaseKey);
            Map<String, MySplitterLoadBalanceConfig> nodeLoadBalance = mySplitterDataBaseConfig.getLoadBalance();
            if (nodeLoadBalance == null || nodeLoadBalance.size() == 0) {
                // 如果子节点的loadBalance是空，就设置为父节点的loadBalance
                nodeLoadBalance = mySplitterConfig.getCommon().getLoadBalance();
            } else {
                // 如果节点的loadBalance有填写一项或多项，补充空项，然后检查key是否在允许的范围内，配置是否正确
                for (String supportLbKey : SUPPORT_LB_NODE_MODE_LIST) {
                    if (nodeLoadBalance.get(supportLbKey) == null) {
                        MySplitterLoadBalanceConfig mySplitterLoadBalanceConfig = new MySplitterLoadBalanceConfig();
                        mySplitterLoadBalanceConfig.setEnabled(false);
                        nodeLoadBalance.put(supportLbKey, mySplitterLoadBalanceConfig);
                    }
                }
            }
            isLoadBalanceMapLegal(nodeLoadBalance);
            mySplitterDataBaseConfig.setLoadBalance(nodeLoadBalance);
        }
        // 检查databases
        if (databases.size() == 0) {
            throw new IllegalArgumentException("Databases configuration is empty!");
        }
        for (String databaseKey : databases.keySet()) {
            MySplitterDataBaseConfig mySplitterDataBaseConfig = databases.get(databaseKey);
            // 如果没有设置reader或者writer，报错
            if ((mySplitterDataBaseConfig.getReaders() == null ||
                    mySplitterDataBaseConfig.getReaders().size() == 0) &&
                    (mySplitterDataBaseConfig.getWriters() == null ||
                            mySplitterDataBaseConfig.getWriters().size() == 0) &&
                    (mySplitterDataBaseConfig.getIntegrates() == null ||
                            mySplitterDataBaseConfig.getIntegrates().size() == 0)) {
                throw new IllegalArgumentException("Database named " + databaseKey + " must contains one of reader " +
                        "writer or integrates!");
            }
            // 如果设置了integrates但是设置了reader或writer，报错
            if ((mySplitterDataBaseConfig.getIntegrates() != null &&
                    mySplitterDataBaseConfig.getIntegrates().size() > 0) &&
                    (((mySplitterDataBaseConfig.getReaders() != null &&
                            mySplitterDataBaseConfig.getReaders().size() > 0)) ||
                            ((mySplitterDataBaseConfig.getWriters() != null &&
                                    mySplitterDataBaseConfig.getWriters().size() > 0)))) {
                throw new IllegalArgumentException("Database named " + databaseKey + " contains integrates, caused " +
                        "reader and writer is invalid!");
            }
        }
        // 检查数据源实现类是否都已经定义，如果子节点没有设置，将从父节点获取并在子节点设置，同时判断负载均衡权重是否设置（随机负载均衡用），如果没设置，设置为1
        for (String databaseKey : databases.keySet()) {
            MySplitterDataBaseConfig mySplitterDataBaseConfig = databases.get(databaseKey);
            Map<String, MySplitterDataSourceNodeConfig> integrates = mySplitterDataBaseConfig.getIntegrates();
            if (integrates != null) {
                for (String integrateKey : integrates.keySet()) {
                    MySplitterDataSourceNodeConfig mySplitterDataSourceNodeConfig = integrates.get(integrateKey);
                    checkAndImproveDataSourceNode(databaseKey, integrateKey,
                            mySplitterDataSourceNodeConfig,
                            mySplitterDataBaseConfig,
                            mySplitterConfig.getCommon());
                }
            }
            Map<String, MySplitterDataSourceNodeConfig> writers = mySplitterDataBaseConfig.getWriters();
            if (writers != null) {
                for (String writerKey : writers.keySet()) {
                    MySplitterDataSourceNodeConfig mySplitterDataSourceNodeConfig = writers.get(writerKey);
                    checkAndImproveDataSourceNode(databaseKey, writerKey,
                            mySplitterDataSourceNodeConfig,
                            mySplitterDataBaseConfig,
                            mySplitterConfig.getCommon());
                }
            }
            Map<String, MySplitterDataSourceNodeConfig> readers = mySplitterDataBaseConfig.getReaders();
            if (readers != null) {
                for (String readerKey : readers.keySet()) {
                    MySplitterDataSourceNodeConfig mySplitterDataSourceNodeConfig = readers.get(readerKey);
                    checkAndImproveDataSourceNode(databaseKey, readerKey,
                            mySplitterDataSourceNodeConfig,
                            mySplitterDataBaseConfig,
                            mySplitterConfig.getCommon());
                }
            }
        }
        // 如果有多个数据库检查是否包含多数据库路由，并且是否合法
        if (databases.size() > 1) {
            ConfigurationUtil.isDatabasesRoutingHandlerLegal(mySplitterConfig.getDatabasesRoutingHandler());
        }
        // 判断读写解析器是否存在，如果存在，检查是否合法；如果不存在，使用默认的读写解析器
        if (StringUtil.isBlank(mySplitterConfig.getReadAndWriteParser())) {
            mySplitterConfig.setReadAndWriteParser("com.mysplitter.DefaultReadAndWriteParser");
        } else {
            ConfigurationUtil.isReadAndWriteParserLegal(mySplitterConfig.getReadAndWriteParser());
        }
        // 检查是否设置数据库密码加密
        if (mySplitterConfig.isEnablePasswordEncryption()) {
            // 如果设置数据库密码加密解密每个数据库密码
            for (String databaseKey : databases.keySet()) {
                MySplitterDataBaseConfig mySplitterDataBaseConfig = databases.get(databaseKey);
                Map<String, MySplitterDataSourceNodeConfig> integrates = mySplitterDataBaseConfig.getIntegrates();
                if (integrates != null) {
                    for (String integrateKey : integrates.keySet()) {
                        MySplitterDataSourceNodeConfig nodeConfig = integrates.get(integrateKey);
                        Object password = nodeConfig.getConfiguration().get("password");
                        Object publicKey = nodeConfig.getConfiguration().get("publicKey");
                        if (password != null && StringUtil.isNotBlank(password.toString())) {
                            if (publicKey == null || StringUtil.isBlank(publicKey.toString())) {
                                throw new IllegalArgumentException("You have setting the database password " +
                                        "encryption, please set the public key!");
                            }
                            // 解密密码
                            String decryptPassword = SecurityUtil.decrypt(publicKey.toString(), password.toString());
                            // 将解密后的密码重新放入配置文件中
                            nodeConfig.getConfiguration().put("password", decryptPassword);
                        }
                    }
                }
                Map<String, MySplitterDataSourceNodeConfig> writers = mySplitterDataBaseConfig.getWriters();
                if (writers != null) {
                    for (String writerKey : writers.keySet()) {
                        MySplitterDataSourceNodeConfig nodeConfig = writers.get(writerKey);
                        Object password = nodeConfig.getConfiguration().get("password");
                        Object publicKey = nodeConfig.getConfiguration().get("publicKey");
                        if (password != null && StringUtil.isNotBlank(password.toString())) {
                            if (publicKey == null || StringUtil.isBlank(publicKey.toString())) {
                                throw new IllegalArgumentException("You have setting the database password " +
                                        "encryption, please set the public key!");
                            }
                            // 解密密码
                            String decryptPassword = SecurityUtil.decrypt(publicKey.toString(), password.toString());
                            // 将解密后的密码重新放入配置文件中
                            nodeConfig.getConfiguration().put("password", decryptPassword);
                        }
                    }
                }
                Map<String, MySplitterDataSourceNodeConfig> readers = mySplitterDataBaseConfig.getReaders();
                if (readers != null) {
                    for (String readerKey : readers.keySet()) {
                        MySplitterDataSourceNodeConfig nodeConfig = readers.get(readerKey);
                        Object password = nodeConfig.getConfiguration().get("password");
                        Object publicKey = nodeConfig.getConfiguration().get("publicKey");
                        if (password != null && StringUtil.isNotBlank(password.toString())) {
                            if (publicKey == null || StringUtil.isBlank(publicKey.toString())) {
                                throw new IllegalArgumentException("You have setting the database password " +
                                        "encryption, please set the public key!");
                            }
                            // 解密密码
                            String decryptPassword = SecurityUtil.decrypt(publicKey.toString(), password.toString());
                            // 将解密后的密码重新放入配置文件中
                            nodeConfig.getConfiguration().put("password", decryptPassword);
                        }
                    }
                }
            }
        }
    }

    /**
     * 检查过滤器是否正确
     */
    private static void isFilterLegal(String MySplitterFilter) throws ClassNotFoundException {
        if (StringUtil.isBlank(MySplitterFilter)) {
            throw new IllegalArgumentException("MySplitter filter list contains empty class name.");
        } else {
            Class<?> aClass = Class.forName(MySplitterFilter);
            Class<?>[] interfaces = aClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                if (anInterface.getName().equals(MySplitterFilterAdvise.class.getName())) {
                    return;
                }
            }
            throw new IllegalArgumentException("MySplitter filter not support " + MySplitterFilter + ", may not " +
                    "implements com.mysplitter.advise.MySplitterFilterAdvise!");
        }
    }

    /**
     * 检查数据源节点是否正确，并且如果没有配置，使用父节点的配置，同时判断负载均衡权重是否设置（随机负载均衡用），如果没设置，设置为1
     */
    private static void checkAndImproveDataSourceNode(String databaseName,
                                                      String dataSourceNodeName,
                                                      MySplitterDataSourceNodeConfig mySplitterDataSourceNodeConfig,
                                                      MySplitterDataBaseConfig mySplitterDataBaseConfig,
                                                      MySplitterCommonConfig common)
            throws DataSourceClassNotDefine, ClassNotFoundException {
        // 检查数据源节点是否正确，并且如果没有配置，使用父节点的配置
        String dataSourceClass = mySplitterDataSourceNodeConfig.getDataSourceClass();
        if (StringUtil.isBlank(dataSourceClass)) {
            dataSourceClass = mySplitterDataBaseConfig.getDataSourceClass();
            if (StringUtil.isBlank(dataSourceClass)) {
                dataSourceClass = common.getDataSourceClass();
                if (StringUtil.isBlank(dataSourceClass)) {
                    throw new DataSourceClassNotDefine(databaseName, dataSourceNodeName);
                }
            }
        }
        if (isDataSourceClassLegal(dataSourceClass)) {
            mySplitterDataSourceNodeConfig.setDataSourceClass(dataSourceClass);
        }
        // 判断负载均衡权重是否设置（随机负载均衡用），如果没设置，设置为1
        Integer weight = mySplitterDataSourceNodeConfig.getWeight();
        if (weight == null) {
            mySplitterDataSourceNodeConfig.setWeight(1);
        }
    }

    /**
     * 判断定义的数据源是否是合法的（存在这个类）
     */
    private static boolean isDataSourceClassLegal(String dataSourceClass) throws ClassNotFoundException {
        try {
            Thread.currentThread().getContextClassLoader().loadClass(dataSourceClass);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundException("DataSource class " + dataSourceClass + " in mysplitter" +
                    ".yml is not legal.");
        }
        return true;
    }

    /**
     * 判断多数据库路由处理器是否是合法的（存在这个类，并且继承MySplitterDatabasesRoutingHandlerAdvise接口
     */
    private static void isDatabasesRoutingHandlerLegal(String databasesRoutingHandler) throws ClassNotFoundException {
        if (StringUtil.isBlank(databasesRoutingHandler)) {
            throw new IllegalArgumentException("DatabasesRoutingHandler is not define.");
        } else {
            Class<?> aClass = Class.forName(databasesRoutingHandler);
            Class<?>[] interfaces = aClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                if (anInterface.getName().equals(MySplitterDatabasesRoutingHandlerAdvise.class.getName())) {
                    return;
                }
            }
            throw new IllegalArgumentException("DatabasesRoutingHandler not support " + databasesRoutingHandler + ", " +
                    "may not implements com.mysplitter.advise.MySplitterDatabasesRoutingHandlerAdvise!");
        }
    }

    /**
     * 判断判断sql读和写的解析器是否合法
     */
    private static void isReadAndWriteParserLegal(String readAndWriteParser) throws ClassNotFoundException {
        if (StringUtil.isBlank(readAndWriteParser)) {
            throw new IllegalArgumentException("ReadAndWriteParser is not define.");
        } else {
            Class<?> aClass = Class.forName(readAndWriteParser);
            Class<?>[] interfaces = aClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                if (anInterface.getName().equals(MySplitterReadAndWriteParserAdvise.class.getName())) {
                    return;
                }
            }
            throw new IllegalArgumentException("ReadAndWriteParser not support " + readAndWriteParser + ", " +
                    "may not implements com.mysplitter.advise.MySplitterReadAndWriteParserAdvise!");
        }
    }

    /**
     * 检查LoadBalance是否合法
     */
    private static void isLoadBalanceMapLegal(Map<String, MySplitterLoadBalanceConfig> loadBalanceMap) {
        for (String loadBalanceKey : loadBalanceMap.keySet()) {
            if (!SUPPORT_LB_NODE_MODE_LIST.contains(loadBalanceKey)) {
                throw new IllegalArgumentException("MySplitter loadBalance not support key " + loadBalanceKey + "! " +
                        "Only supported one of " + SUPPORT_LB_NODE_MODE_LIST + ".");
            }
            MySplitterLoadBalanceConfig mySplitterLoadBalanceConfig = loadBalanceMap.get(loadBalanceKey);
            if (mySplitterLoadBalanceConfig.isEnabled()) {
                String strategy = mySplitterLoadBalanceConfig.getStrategy();
                if (StringUtil.isBlank(strategy)) {
                    throw new IllegalArgumentException("MySplitter loadBalance strategy is empty!" +
                            "Only supported one of " + SUPPORT_STRATEGY_LIST + ".");
                }
                if (!SUPPORT_STRATEGY_LIST.contains(strategy)) {
                    throw new IllegalArgumentException("MySplitter loadBalance not support key " + strategy + "! " +
                            "Only supported one of " + SUPPORT_STRATEGY_LIST + ".");
                }
            }
        }
    }

    /**
     * 检查HighAvailable是否合法
     */
    private static void isHighAvailableMapLegal(Map<String, MySplitterHighAvailableConfig> haConfigMap) {
        for (String haKey : haConfigMap.keySet()) {
            // 检查key是否存在
            if (!SUPPORT_HA_NODE_MODE_LIST.contains(haKey)) {
                throw new IllegalArgumentException("MySplitter highAvailable not support key " + haKey + "! Only " +
                        "supported one of " + SUPPORT_HA_NODE_MODE_LIST + ".");
            }
            // 检查配置是否正确
            MySplitterHighAvailableConfig mySplitterHighAvailableConfig = haConfigMap.get(haKey);
            if (mySplitterHighAvailableConfig.isEnabled()) {
                // 检查心跳sql语句
                if (StringUtil.isBlank(mySplitterHighAvailableConfig.getDetectionSql())) {
                    throw new IllegalArgumentException("MySplitter highAvailable detection sql is empty!");
                }
                // 检查切换时机
                if (StringUtil.isBlank(mySplitterHighAvailableConfig.getSwitchOpportunity())) {
                    throw new IllegalArgumentException("MySplitter highAvailable switch opportunity is empty!");
                }
                String switchOpportunity = mySplitterHighAvailableConfig.getSwitchOpportunity();
                if (!SUPPORT_SWITCH_OPPORTUNITIES_LIST.contains(switchOpportunity)) {
                    throw new IllegalArgumentException("MySplitter highAvailable switch opportunity not support key "
                            + switchOpportunity + "! Only supported one of " + SUPPORT_SWITCH_OPPORTUNITIES_LIST + ".");
                }
                // 检查健康的数据源检查速率和不健康的数据源检查速率
                String healthyHeartbeatRate = mySplitterHighAvailableConfig.getHealthyHeartbeatRate();
                if (!isHighAvailableHeartbeatRateLegal(healthyHeartbeatRate)) {
                    throw new IllegalArgumentException("MySplitter highAvailable healthy heartbeat rate is not support "
                            + healthyHeartbeatRate + "!");
                }
                String illHeartbeatRate = mySplitterHighAvailableConfig.getIllHeartbeatRate();
                if (!isHighAvailableHeartbeatRateLegal(illHeartbeatRate)) {
                    throw new IllegalArgumentException("MySplitter highAvailable ill heartbeat rate is not support "
                            + illHeartbeatRate + "!");
                }
                // 检查不健康数据源警告处理器
                isHighAvailableIllAlertHandlerLegal(mySplitterHighAvailableConfig.getIllAlertHandler());
            }
        }
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
        } else if (heartbeatRate.equals("0")) {
            return true;
        }
        return false;
    }

    private static boolean isHighAvailableIllAlertHandlerLegal(String illAlertHandler) {
        Class<?> aClass = null;
        try {
            aClass = Class.forName(illAlertHandler);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("HighAvailable ill alert handler " + illAlertHandler + " in " +
                    "mysplitter" +
                    ".yml is not legal!");
        }
        Class<?>[] interfaces = aClass.getInterfaces();
        for (Class<?> anInterface : interfaces) {
            if (anInterface.getName().equals(MySplitterDataSourceIllAlerterAdvise.class.getName())) {
                return true;
            }
        }
        throw new IllegalArgumentException("HighAvailable ill alert handler not support " +
                illAlertHandler + ", may not implements com.mysplitter.advise" +
                ".MySplitterDataSourceIllAlerterAdvise!");
    }

}
