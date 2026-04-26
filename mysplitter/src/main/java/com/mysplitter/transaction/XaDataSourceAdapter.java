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

package com.mysplitter.transaction;

import com.mysplitter.util.StringUtil;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;

public class XaDataSourceAdapter {

    private static final String DATA_SOURCE_CLASS_KEY = "dataSourceClass";

    private static final String XA_DATA_SOURCE_CLASS_KEY = "xaDataSourceClass";

    private static final String XA_PROPERTIES_KEY = "xaProperties";

    private final String resourceId;

    private final XADataSource xaDataSource;

    public XaDataSourceAdapter(String resourceId, XADataSource xaDataSource) {
        if (StringUtil.isBlank(resourceId)) {
            throw new IllegalArgumentException("MySplitter XA resourceId is empty.");
        }
        if (xaDataSource == null) {
            throw new IllegalArgumentException("MySplitter XADataSource is null.");
        }
        this.resourceId = resourceId;
        this.xaDataSource = xaDataSource;
    }

    public static XaDataSourceAdapter fromClass(String resourceId,
                                                String xaDataSourceClassName,
                                                Map<String, Object> configuration) throws Exception {
        if (StringUtil.isBlank(xaDataSourceClassName)) {
            throw new IllegalArgumentException("MySplitter xaDataSourceClass is empty.");
        }
        Class<?> xaDataSourceClass = Thread.currentThread().getContextClassLoader().loadClass(xaDataSourceClassName);
        if (!XADataSource.class.isAssignableFrom(xaDataSourceClass)) {
            throw new IllegalArgumentException("MySplitter xaDataSourceClass " + xaDataSourceClassName +
                    " does not implement javax.sql.XADataSource.");
        }
        XADataSource xaDataSource = (XADataSource) xaDataSourceClass.getConstructor().newInstance();
        applyConfiguration(xaDataSource, configuration);
        return new XaDataSourceAdapter(resourceId, xaDataSource);
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getXaDataSourceClassName() {
        return xaDataSource.getClass().getName();
    }

    public XADataSource getXaDataSource() {
        return xaDataSource;
    }

    public XaConnectionBranch openBranch(String globalTransactionId,
                                         String branchId,
                                         String username,
                                         String password) throws SQLException {
        XAConnection xaConnection = openXaConnection(username, password);
        Connection connection = null;
        try {
            connection = xaConnection.getConnection();
            XAResource xaResource = xaConnection.getXAResource();
            XaBranchTransaction branchTransaction =
                    new XaBranchTransaction(globalTransactionId, branchId, resourceId, xaResource);
            return new XaConnectionBranch(xaConnection, connection, branchTransaction);
        } catch (SQLException e) {
            closeQuietly(connection, xaConnection);
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(connection, xaConnection);
            throw e;
        }
    }

    private XAConnection openXaConnection(String username, String password) throws SQLException {
        if (username != null || password != null) {
            return xaDataSource.getXAConnection(username, password);
        }
        return xaDataSource.getXAConnection();
    }

    private void closeQuietly(Connection connection, XAConnection xaConnection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // Ignore cleanup failures while preserving the original open error.
            }
        }
        if (xaConnection != null) {
            try {
                xaConnection.close();
            } catch (Exception ignored) {
                // Ignore cleanup failures while preserving the original open error.
            }
        }
    }

    private static void applyConfiguration(XADataSource xaDataSource, Map<String, Object> configuration)
            throws Exception {
        Map<String, Object> xaConfiguration = createXaConfiguration(configuration);
        BeanInfo beanInfo = Introspector.getBeanInfo(xaDataSource.getClass(), Object.class);
        for (PropertyDescriptor propertyDescriptor : beanInfo.getPropertyDescriptors()) {
            Method writeMethod = propertyDescriptor.getWriteMethod();
            if (writeMethod == null) {
                continue;
            }
            Object value = xaConfiguration.get(propertyDescriptor.getName());
            if (value == null) {
                continue;
            }
            Class<?>[] parameterTypes = writeMethod.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException("Not support XA property " + propertyDescriptor.getName());
            }
            writeMethod.invoke(xaDataSource, convertValue(parameterTypes[0], value));
        }
    }

    private static Map<String, Object> createXaConfiguration(Map<String, Object> configuration) {
        Map<String, Object> xaConfiguration = new LinkedHashMap<String, Object>();
        if (configuration != null) {
            for (Map.Entry<String, Object> entry : configuration.entrySet()) {
                String key = entry.getKey();
                if (DATA_SOURCE_CLASS_KEY.equals(key) || XA_DATA_SOURCE_CLASS_KEY.equals(key) ||
                        XA_PROPERTIES_KEY.equals(key)) {
                    continue;
                }
                xaConfiguration.put(key, entry.getValue());
            }
            Object xaProperties = configuration.get(XA_PROPERTIES_KEY);
            if (xaProperties != null) {
                if (!(xaProperties instanceof Map)) {
                    throw new IllegalArgumentException("MySplitter xaProperties must be a map.");
                }
                Map<?, ?> xaPropertiesMap = (Map<?, ?>) xaProperties;
                for (Map.Entry<?, ?> entry : xaPropertiesMap.entrySet()) {
                    xaConfiguration.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return xaConfiguration;
    }

    private static Object convertValue(Class<?> parameterType, Object value) {
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
}
