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

import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterLoadBalanceConfig;
import com.mysplitter.util.ClassLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数据源包装类
 */
public class DataSourceWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceWrapper.class);

    /**
     * 标志是否已经初始化
     */
    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * 包装类构造方法
     *
     * @param nodeName     节点名称
     * @param dataBaseName 数据库名称
     * @param nodeConfig   节点配置
     */
    public DataSourceWrapper(String nodeName,
                             String dataBaseName,
                             MySplitterDataSourceNodeConfig nodeConfig,
                             MySplitterLoadBalanceConfig loadBalanceConfig) {
        this.nodeName = nodeName;
        this.dataBaseName = dataBaseName;
        this.nodeConfig = nodeConfig;
        this.loadBalanceConfig = loadBalanceConfig;
    }

    private DataSource realDataSource;

    private String nodeName;

    private String dataBaseName;

    private MySplitterDataSourceNodeConfig nodeConfig;

    private MySplitterLoadBalanceConfig loadBalanceConfig;

    /**
     * 获取真正数据源
     */
    public DataSource getRealDataSource() {
        return realDataSource;
    }

    /**
     * 初始化真正的数据源
     */
    public synchronized void initRealDataSource() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                // 获取真实数据源的全限定名
                String dataSourceClass = nodeConfig.getDataSourceClass();
                LOGGER.info("MySplitter is initializing data source {}, database named {}, node named {}",
                        dataSourceClass, dataBaseName, nodeName);
                // 创建真实数据源
                DataSource dataSource = ClassLoaderUtil.getInstance(dataSourceClass, DataSource.class);
                // 使用内省机制赋予状态
                BeanInfo beanInfo = Introspector.getBeanInfo(dataSource.getClass(), Object.class);
                PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
                for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                    Method writeMethod = propertyDescriptor.getWriteMethod();
                    if (writeMethod != null) {
                        // 获取内省机制中的字段名（首字母小写）
                        String name = writeMethod.getName();
                        name = name.substring(3, 4).toLowerCase() + name.substring(4);
                        // 获取用户输入的map中的数据并设置
                        Object value = this.nodeConfig.getConfiguration().get(name);
                        Class<?>[] parameterTypes = writeMethod.getParameterTypes();
                        if (parameterTypes.length > 1) {
                            throw new IllegalArgumentException("Not support properties " + name);
                        }
                        if (value != null) {
                            Class<?> parameterType = parameterTypes[0];
                            Object paramVal;
                            if (parameterType == Integer.class) {
                                paramVal = Integer.parseInt((String) value);
                            } else if (parameterType == Long.class) {
                                paramVal = Long.parseLong((String) value);
                            } else if (parameterType == String.class) {
                                paramVal = value;
                            } else {
                                paramVal = value;
                            }
                            writeMethod.invoke(dataSource, paramVal);
                        }
                    }
                }
                // 创建引用
                this.realDataSource = dataSource;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 关闭真正的数据源
     */
    public synchronized void releaseRealDataSource() throws Exception {
        if (isInitialized.get()) {
            // 内省机制查找关闭资源的方法，并执行
            BeanInfo beanInfo = Introspector.getBeanInfo(this.realDataSource.getClass(), Object.class);
            MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
            for (MethodDescriptor methodDescriptor : methodDescriptors) {
                Method method = methodDescriptor.getMethod();
                if (method != null) {
                    if ("close".equals(method.getName()) && method.getParameterTypes().length == 0) {
                        LOGGER.info("MySplitter is closing data source {}, database named {}, node named {}",
                                method.getDeclaringClass(), dataBaseName, nodeName);
                        method.invoke(this.realDataSource);
                    }
                }
            }
            isInitialized.set(false);
        }
    }

    /**
     * 获取节点名称（监控用）
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * 获取数据库名称（监控用）
     */
    public String getDataBaseName() {
        return dataBaseName;
    }

    /**
     * 获取数据源的配置（监控用）
     */
    public MySplitterDataSourceNodeConfig getNodeConfig() {
        return nodeConfig;
    }

    public MySplitterLoadBalanceConfig getLoadBalanceConfig() {
        return loadBalanceConfig;
    }

    @Override
    public String toString() {
        return "DataSourceWrapper{" +
                "nodeName='" + nodeName + '\'' +
                ", dataBaseName='" + dataBaseName + '\'' +
                '}';
    }
}
