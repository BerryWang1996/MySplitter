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

import com.mysplitter.advise.DataSourceFilterAdvise;
import com.mysplitter.advise.DataSourceIllAlerterAdvise;
import com.mysplitter.advise.DatabasesRoutingHandlerAdvise;
import com.mysplitter.advise.ReadAndWriteParserAdvise;
import com.mysplitter.config.*;
import com.mysplitter.exceptions.DataSourceClassNotDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置文件工具类
 */
public class ConfigurationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationUtil.class);

    private static final List<String> SUPPORT_SWITCH_OPPORTUNITIES_LIST =
            Arrays.asList("on-error", "scheduled", "on-error-dissolve");

    private static final List<String> SUPPORT_STRATEGY_LIST =
            Arrays.asList("polling", "random");

    private static final List<String> SUPPORT_HA_NODE_MODE_LIST =
            Arrays.asList("integrate", "read", "write");

    private static final List<String> SUPPORT_LB_NODE_MODE_LIST =
            Arrays.asList("read", "write");

    private static final Pattern SECRET_PLACEHOLDER_PATTERN =
            Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?\\}");

    private enum PasswordSourceMode {
        PLAIN,
        ENVIRONMENT,
        LEGACY_RSA
    }

    private ConfigurationUtil() {
    }

    public static MySplitterRootConfig getMySplitterConfig(String fileName) throws Exception {
        // 如果配置文件不存在报错
        if (fileName == null || !new File(fileName).exists()) {
            throw new FileNotFoundException("MySplitter configuration file " + fileName + " not found!");
        }
        // 读取配置文件
        try (InputStream resource = new FileInputStream(new File(fileName))) {
        // 饿汉式加载配置对象
            return loadMySplitterConfig(resource);
        }
    }

    public static MySplitterRootConfig getMySplitterConfig(InputStream resource, String resourceName) throws Exception {
        if (resource == null) {
            throw new FileNotFoundException("MySplitter configuration resource " + resourceName + " not found!");
        }
        try (InputStream inputStream = resource) {
            return loadMySplitterConfig(inputStream);
        }
    }

    private static MySplitterRootConfig loadMySplitterConfig(InputStream inputStream) {
        Object yamlRoot = newYaml().load(inputStream);
        if (yamlRoot == null) {
            return new MySplitterRootConfig();
        }
        return toRootConfig(asMap(yamlRoot, "root"));
    }

    private static Yaml newYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        DumperOptions dumperOptions = new DumperOptions();
        return new Yaml(new SafeConstructor(loaderOptions), new Representer(dumperOptions),
                dumperOptions, loaderOptions, new Resolver());
    }

    private static MySplitterRootConfig toRootConfig(Map<?, ?> rootMap) {
        MySplitterRootConfig rootConfig = new MySplitterRootConfig();
        Object mySplitterValue = rootMap.get("mysplitter");
        if (mySplitterValue != null) {
            rootConfig.setMysplitter(toMySplitterConfig(asMap(mySplitterValue, "mysplitter")));
        }
        return rootConfig;
    }

    private static MySplitterConfig toMySplitterConfig(Map<?, ?> configMap) {
        MySplitterConfig config = new MySplitterConfig();
        config.setDatabasesRoutingHandler(asString(configMap.get("databasesRoutingHandler")));
        config.setReadAndWriteParser(asString(configMap.get("readAndWriteParser")));
        config.setEnablePasswordEncryption(asBoolean(configMap.get("enablePasswordEncryption"), false));
        config.setPasswordSource(asString(configMap.get("passwordSource")));
        config.setIllAlertHandler(asString(configMap.get("illAlertHandler")));
        config.setFilters(asStringList(configMap.get("filters"), "mysplitter.filters"));
        config.setCommon(toCommonConfig(optionalMap(configMap.get("common"), "mysplitter.common")));
        config.setDatabases(toDatabaseMap(configMap.get("databases"), "mysplitter.databases"));
        return config;
    }

    private static MySplitterCommonConfig toCommonConfig(Map<?, ?> commonMap) {
        if (commonMap == null) {
            return null;
        }
        MySplitterCommonConfig commonConfig = new MySplitterCommonConfig();
        commonConfig.setDataSourceClass(asString(commonMap.get("dataSourceClass")));
        commonConfig.setLoadBalance(toLoadBalanceMap(commonMap.get("loadBalance"), "mysplitter.common.loadBalance"));
        return commonConfig;
    }

    private static LinkedHashMap<String, MySplitterDataBaseConfig> toDatabaseMap(Object value, String path) {
        Map<?, ?> databasesMap = optionalMap(value, path);
        if (databasesMap == null) {
            return null;
        }
        LinkedHashMap<String, MySplitterDataBaseConfig> databases =
                new LinkedHashMap<String, MySplitterDataBaseConfig>();
        for (Map.Entry<?, ?> entry : databasesMap.entrySet()) {
            String databaseName = keyToString(entry.getKey(), path);
            databases.put(databaseName, toDatabaseConfig(optionalMap(entry.getValue(), path + "." + databaseName),
                    path + "." + databaseName));
        }
        return databases;
    }

    private static MySplitterDataBaseConfig toDatabaseConfig(Map<?, ?> databaseMap, String path) {
        if (databaseMap == null) {
            return null;
        }
        MySplitterDataBaseConfig dataBaseConfig = new MySplitterDataBaseConfig();
        dataBaseConfig.setDataSourceClass(asString(databaseMap.get("dataSourceClass")));
        dataBaseConfig.setLoadBalance(toLoadBalanceMap(databaseMap.get("loadBalance"), path + ".loadBalance"));
        dataBaseConfig.setIntegrates(toDataSourceNodeMap(databaseMap.get("integrates"), path + ".integrates"));
        dataBaseConfig.setReaders(toDataSourceNodeMap(databaseMap.get("readers"), path + ".readers"));
        dataBaseConfig.setWriters(toDataSourceNodeMap(databaseMap.get("writers"), path + ".writers"));
        return dataBaseConfig;
    }

    private static Map<String, MySplitterLoadBalanceConfig> toLoadBalanceMap(Object value, String path) {
        Map<?, ?> loadBalanceMap = optionalMap(value, path);
        if (loadBalanceMap == null) {
            return null;
        }
        Map<String, MySplitterLoadBalanceConfig> loadBalances =
                new LinkedHashMap<String, MySplitterLoadBalanceConfig>();
        for (Map.Entry<?, ?> entry : loadBalanceMap.entrySet()) {
            String name = keyToString(entry.getKey(), path);
            loadBalances.put(name, toLoadBalanceConfig(optionalMap(entry.getValue(), path + "." + name),
                    path + "." + name));
        }
        return loadBalances;
    }

    private static MySplitterLoadBalanceConfig toLoadBalanceConfig(Map<?, ?> loadBalanceMap, String path) {
        if (loadBalanceMap == null) {
            return null;
        }
        MySplitterLoadBalanceConfig loadBalanceConfig = new MySplitterLoadBalanceConfig();
        loadBalanceConfig.setEnabled(asBoolean(loadBalanceMap.get("enabled"), false));
        loadBalanceConfig.setStrategy(asString(loadBalanceMap.get("strategy")));
        loadBalanceConfig.setDatabaseName(asString(loadBalanceMap.get("databaseName")));
        String failTimeout = asString(loadBalanceMap.get("failTimeout"));
        if (failTimeout != null) {
            loadBalanceConfig.setFailTimeout(failTimeout);
        }
        return loadBalanceConfig;
    }

    private static LinkedHashMap<String, MySplitterDataSourceNodeConfig> toDataSourceNodeMap(Object value, String path) {
        Map<?, ?> nodesMap = optionalMap(value, path);
        if (nodesMap == null) {
            return null;
        }
        LinkedHashMap<String, MySplitterDataSourceNodeConfig> nodes =
                new LinkedHashMap<String, MySplitterDataSourceNodeConfig>();
        for (Map.Entry<?, ?> entry : nodesMap.entrySet()) {
            String nodeName = keyToString(entry.getKey(), path);
            nodes.put(nodeName, toDataSourceNodeConfig(optionalMap(entry.getValue(), path + "." + nodeName),
                    path + "." + nodeName));
        }
        return nodes;
    }

    private static MySplitterDataSourceNodeConfig toDataSourceNodeConfig(Map<?, ?> nodeMap, String path) {
        if (nodeMap == null) {
            return null;
        }
        MySplitterDataSourceNodeConfig nodeConfig = new MySplitterDataSourceNodeConfig();
        nodeConfig.setDataSourceClass(asString(nodeMap.get("dataSourceClass")));
        nodeConfig.setWeight(asInteger(nodeMap.get("weight"), path + ".weight"));
        nodeConfig.setConfiguration(toObjectMap(nodeMap.get("configuration"), path + ".configuration"));
        return nodeConfig;
    }

    private static Map<String, Object> toObjectMap(Object value, String path) {
        Map<?, ?> sourceMap = optionalMap(value, path);
        if (sourceMap == null) {
            return null;
        }
        Map<String, Object> targetMap = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            targetMap.put(keyToString(entry.getKey(), path), entry.getValue());
        }
        return targetMap;
    }

    private static List<String> asStringList(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Configuration " + path + " must be a list.");
        }
        List<?> sourceList = (List<?>) value;
        List<String> targetList = new ArrayList<String>();
        for (Object item : sourceList) {
            targetList.add(asString(item));
        }
        return targetList;
    }

    private static Map<?, ?> optionalMap(Object value, String path) {
        if (value == null) {
            return null;
        }
        return asMap(value, path);
    }

    private static Map<?, ?> asMap(Object value, String path) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Configuration " + path + " must be a map.");
        }
        return (Map<?, ?>) value;
    }

    private static String keyToString(Object key, String path) {
        String stringKey = asString(key);
        if (StringUtil.isBlank(stringKey)) {
            throw new IllegalArgumentException("Configuration " + path + " contains an empty key.");
        }
        return stringKey;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Configuration " + path + " must be an integer.", e);
        }
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return Boolean.valueOf(value.toString()).booleanValue();
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
        // 检查common是否为空，如果为空创建一个新的
        if (mySplitterConfig.getCommon() == null) {
            mySplitterConfig.setCommon(new MySplitterCommonConfig());
        }
        // 检查不健康数据源警告处理器，如果没设置数据源异常处理器，设置为默认的数据源异常处理器
        if (StringUtil.isBlank(mySplitterConfig.getIllAlertHandler())) {
            mySplitterConfig.setIllAlertHandler("com.mysplitter.DefaultDataSourceIllAlertHandler");
        }
        isHighAvailableIllAlertHandlerLegal(mySplitterConfig.getIllAlertHandler());
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
        applyPasswordSource(databases, resolvePasswordSourceMode(mySplitterConfig));
    }

    private static PasswordSourceMode resolvePasswordSourceMode(MySplitterConfig config) {
        String passwordSource = config.getPasswordSource();
        if (StringUtil.isBlank(passwordSource)) {
            return config.isEnablePasswordEncryption() ? PasswordSourceMode.LEGACY_RSA : PasswordSourceMode.PLAIN;
        }

        String normalized = passwordSource.trim().toLowerCase(Locale.ENGLISH);
        if ("plain".equals(normalized)) {
            return PasswordSourceMode.PLAIN;
        }
        if ("environment".equals(normalized) || "env".equals(normalized)) {
            return PasswordSourceMode.ENVIRONMENT;
        }
        if ("legacy-rsa".equals(normalized) || "rsa".equals(normalized)) {
            return PasswordSourceMode.LEGACY_RSA;
        }
        throw new IllegalArgumentException("MySplitter passwordSource not support " + passwordSource +
                ". Only supported one of [plain, environment, legacy-rsa].");
    }

    private static void applyPasswordSource(Map<String, MySplitterDataBaseConfig> databases,
                                            PasswordSourceMode passwordSourceMode) throws Exception {
        if (PasswordSourceMode.PLAIN.equals(passwordSourceMode)) {
            return;
        }
        if (PasswordSourceMode.LEGACY_RSA.equals(passwordSourceMode)) {
            LOGGER.warn("MySplitter legacy RSA passwordSource is enabled. This mode is kept for compatibility only; " +
                    "use plain values for local development or environment/external secrets for production.");
        }
        for (String databaseKey : databases.keySet()) {
            MySplitterDataBaseConfig databaseConfig = databases.get(databaseKey);
            applyPasswordSourceToNodes(databaseKey, "integrates", databaseConfig.getIntegrates(), passwordSourceMode);
            applyPasswordSourceToNodes(databaseKey, "writers", databaseConfig.getWriters(), passwordSourceMode);
            applyPasswordSourceToNodes(databaseKey, "readers", databaseConfig.getReaders(), passwordSourceMode);
        }
    }

    private static void applyPasswordSourceToNodes(String databaseKey,
                                                   String nodeGroup,
                                                   Map<String, MySplitterDataSourceNodeConfig> nodes,
                                                   PasswordSourceMode passwordSourceMode) throws Exception {
        if (nodes == null) {
            return;
        }
        for (String nodeKey : nodes.keySet()) {
            MySplitterDataSourceNodeConfig nodeConfig = nodes.get(nodeKey);
            Map<String, Object> configuration = nodeConfig.getConfiguration();
            if (configuration == null) {
                continue;
            }
            String path = "mysplitter.databases." + databaseKey + "." + nodeGroup + "." + nodeKey + ".configuration";
            if (PasswordSourceMode.LEGACY_RSA.equals(passwordSourceMode)) {
                decryptLegacyRsaPassword(configuration, path);
            } else if (PasswordSourceMode.ENVIRONMENT.equals(passwordSourceMode)) {
                resolveEnvironmentPassword(configuration, path);
            }
        }
    }

    private static void decryptLegacyRsaPassword(Map<String, Object> configuration, String path) throws Exception {
        Object password = configuration.get("password");
        if (password == null || StringUtil.isBlank(password.toString())) {
            return;
        }
        Object publicKey = configuration.get("publicKey");
        if (publicKey == null || StringUtil.isBlank(publicKey.toString())) {
            throw new IllegalArgumentException("Configuration " + path +
                    " uses legacy-rsa passwordSource, please set publicKey explicitly.");
        }
        configuration.put("password", SecurityUtil.decrypt(publicKey.toString(), password.toString()));
    }

    private static void resolveEnvironmentPassword(Map<String, Object> configuration, String path) {
        Object passwordEnv = configuration.get("passwordEnv");
        if (passwordEnv != null && StringUtil.isNotBlank(passwordEnv.toString())) {
            configuration.put("password", resolveRequiredSecret(passwordEnv.toString(), path + ".passwordEnv"));
            return;
        }

        Object password = configuration.get("password");
        if (password == null) {
            return;
        }
        String passwordText = password.toString();
        if (!containsSecretPlaceholder(passwordText)) {
            throw new IllegalArgumentException("Configuration " + path +
                    ".password uses passwordSource environment, please use passwordEnv or ${ENV_NAME} placeholder.");
        }
        configuration.put("password", resolveSecretPlaceholders(passwordText, path + ".password"));
    }

    private static boolean containsSecretPlaceholder(String value) {
        return value != null && SECRET_PLACEHOLDER_PATTERN.matcher(value).find();
    }

    private static String resolveSecretPlaceholders(String value, String path) {
        Matcher matcher = SECRET_PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String secretName = matcher.group(1).trim();
            String defaultValue = matcher.group(2);
            String replacement = lookupSecret(secretName);
            if (replacement == null) {
                replacement = defaultValue;
            }
            if (replacement == null) {
                throw new IllegalArgumentException("Configuration " + path +
                        " references missing environment or system property " + secretName + ".");
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String resolveRequiredSecret(String secretName, String path) {
        String resolved = lookupSecret(secretName);
        if (resolved == null) {
            throw new IllegalArgumentException("Configuration " + path +
                    " references missing environment or system property " + secretName + ".");
        }
        return resolved;
    }

    private static String lookupSecret(String secretName) {
        String trimmedSecretName = secretName == null ? null : secretName.trim();
        if (StringUtil.isBlank(trimmedSecretName)) {
            return null;
        }
        String systemProperty = System.getProperty(trimmedSecretName);
        if (systemProperty != null) {
            return systemProperty;
        }
        return System.getenv(trimmedSecretName);
    }

    /**
     * 检查过滤器是否正确
     */
    private static void isFilterLegal(String filterClzName) throws ClassNotFoundException {
        if (StringUtil.isBlank(filterClzName)) {
            throw new IllegalArgumentException("MySplitter filter list contains empty class name.");
        } else {
            Class<?> aClass = Class.forName(filterClzName);
            Class<?>[] interfaces = aClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                if (anInterface.getName().equals(DataSourceFilterAdvise.class.getName())) {
                    return;
                }
            }
            throw new IllegalArgumentException("MySplitter filter not support " + filterClzName + ", may not " +
                    "implements com.mysplitter.advise.DataSourceFilterAdvise!");
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
                if (anInterface.getName().equals(DatabasesRoutingHandlerAdvise.class.getName())) {
                    return;
                }
            }
            throw new IllegalArgumentException("DatabasesRoutingHandler not support " + databasesRoutingHandler + ", " +
                    "may not implements com.mysplitter.advise.DatabasesRoutingHandlerAdvise!");
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
                if (anInterface.getName().equals(ReadAndWriteParserAdvise.class.getName())) {
                    return;
                }
            }
            throw new IllegalArgumentException("ReadAndWriteParser not support " + readAndWriteParser + ", " +
                    "may not implements com.mysplitter.advise.ReadAndWriteParserAdvise!");
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

    private static void isHighAvailableIllAlertHandlerLegal(String illAlertHandler) {
        Class<?> aClass = null;
        try {
            aClass = Class.forName(illAlertHandler);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("HighAvailable ill alert handler " + illAlertHandler + " in " +
                    "mysplitter.yml is not legal!");
        }
        Class<?>[] interfaces = aClass.getInterfaces();
        for (Class<?> anInterface : interfaces) {
            if (anInterface.getName().equals(DataSourceIllAlerterAdvise.class.getName())) {
                return;
            }
        }
        throw new IllegalArgumentException("HighAvailable ill alert handler not support " +
                illAlertHandler + ", may not implements com.mysplitter.advise" +
                ".DataSourceIllAlerterAdvise!");
    }

}
