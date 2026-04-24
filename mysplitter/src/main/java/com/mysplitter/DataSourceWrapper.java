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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataSourceWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceWrapper.class);

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private volatile DataSource realDataSource;

    private final String nodeName;

    private final String dataBaseName;

    private final MySplitterDataSourceNodeConfig nodeConfig;

    private final MySplitterLoadBalanceConfig loadBalanceConfig;

    public DataSourceWrapper(String nodeName,
                             String dataBaseName,
                             MySplitterDataSourceNodeConfig nodeConfig,
                             MySplitterLoadBalanceConfig loadBalanceConfig) {
        this.nodeName = nodeName;
        this.dataBaseName = dataBaseName;
        this.nodeConfig = nodeConfig;
        this.loadBalanceConfig = loadBalanceConfig;
    }

    public DataSource getRealDataSource() {
        return realDataSource;
    }

    public synchronized void initRealDataSource() throws Exception {
        if (!isInitialized.compareAndSet(false, true)) {
            return;
        }
        String dataSourceClass = nodeConfig.getDataSourceClass();
        Map<String, Object> configuration = nodeConfig.getConfiguration();
        if (configuration == null) {
            isInitialized.set(false);
            throw new IllegalArgumentException("Configuration of datasource node " + nodeName + " is empty.");
        }
        try {
            LOGGER.info("MySplitter is initializing data source {}, database named {}, node named {}",
                    dataSourceClass, dataBaseName, nodeName);
            DataSource dataSource = ClassLoaderUtil.getInstance(dataSourceClass, DataSource.class);
            BeanInfo beanInfo = Introspector.getBeanInfo(dataSource.getClass(), Object.class);
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
            for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                Method writeMethod = propertyDescriptor.getWriteMethod();
                if (writeMethod == null) {
                    continue;
                }
                Object value = configuration.get(propertyDescriptor.getName());
                if (value == null) {
                    continue;
                }
                Class<?>[] parameterTypes = writeMethod.getParameterTypes();
                if (parameterTypes.length != 1) {
                    throw new IllegalArgumentException("Not support properties " + propertyDescriptor.getName());
                }
                writeMethod.invoke(dataSource, convertValue(parameterTypes[0], value));
            }
            this.realDataSource = dataSource;
        } catch (Exception e) {
            this.realDataSource = null;
            isInitialized.set(false);
            throw e;
        }
    }

    public synchronized void releaseRealDataSource() throws Exception {
        if (!isInitialized.get() || this.realDataSource == null) {
            return;
        }
        DataSource dataSource = this.realDataSource;
        Exception releaseException = null;
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(dataSource.getClass(), Object.class);
            MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
            for (MethodDescriptor methodDescriptor : methodDescriptors) {
                Method method = methodDescriptor.getMethod();
                if (method != null && "close".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    LOGGER.info("MySplitter is closing data source {}, database named {}, node named {}",
                            method.getDeclaringClass(), dataBaseName, nodeName);
                    method.invoke(dataSource);
                    break;
                }
            }
        } catch (Exception e) {
            releaseException = e;
        } finally {
            this.realDataSource = null;
            isInitialized.set(false);
        }
        if (releaseException != null) {
            throw releaseException;
        }
    }

    private Object convertValue(Class<?> parameterType, Object value) {
        if (String.class.equals(parameterType)) {
            return value.toString();
        }
        if (Integer.class.equals(parameterType) || Integer.TYPE.equals(parameterType)) {
            return Integer.valueOf(value.toString());
        }
        if (Long.class.equals(parameterType) || Long.TYPE.equals(parameterType)) {
            return Long.valueOf(value.toString());
        }
        if (Boolean.class.equals(parameterType) || Boolean.TYPE.equals(parameterType)) {
            return Boolean.valueOf(value.toString());
        }
        if (Double.class.equals(parameterType) || Double.TYPE.equals(parameterType)) {
            return Double.valueOf(value.toString());
        }
        if (Float.class.equals(parameterType) || Float.TYPE.equals(parameterType)) {
            return Float.valueOf(value.toString());
        }
        if (Short.class.equals(parameterType) || Short.TYPE.equals(parameterType)) {
            return Short.valueOf(value.toString());
        }
        if (Byte.class.equals(parameterType) || Byte.TYPE.equals(parameterType)) {
            return Byte.valueOf(value.toString());
        }
        return value;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getDataBaseName() {
        return dataBaseName;
    }

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
