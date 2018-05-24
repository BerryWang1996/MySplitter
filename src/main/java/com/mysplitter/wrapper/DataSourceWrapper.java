package com.mysplitter.wrapper;

import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.util.ClassLoaderUtil;
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
 *
 * @Author: wangbor
 * @Date: 2018/5/18 9:49
 */
public class DataSourceWrapper {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DataSourceWrapper.class);

    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    public DataSourceWrapper(String nodeName, String dataBaseName, MySplitterDataSourceNodeConfig
            mySplitterDataSourceNodeConfig) {
        this.nodeName = nodeName;
        this.dataBaseName = dataBaseName;
        this.mySplitterDataSourceNodeConfig = mySplitterDataSourceNodeConfig;
    }

    private DataSource realDataSource;

    private String nodeName;

    private String dataBaseName;

    private MySplitterDataSourceNodeConfig mySplitterDataSourceNodeConfig;

    public DataSource getRealDataSource() {
        return realDataSource;
    }

    public synchronized void initRealDataSource() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                // 获取真实数据源的全限定名
                String dataSourceClass = mySplitterDataSourceNodeConfig.getDataSourceClass();
                LOGGER.debug("MySplitter is initializing data source {}, database named {}, node named {}",
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
                        name = name.substring(3, 4).toLowerCase() + name.substring(4, name.length());
                        // 获取用户输入的map中的数据并设置
                        Object value = this.mySplitterDataSourceNodeConfig.getConfiguration().get(name);
                        if (value != null) {
                            writeMethod.invoke(dataSource, value);
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

    public String getNodeName() {
        return nodeName;
    }

    public String getDataBaseName() {
        return dataBaseName;
    }

    public MySplitterDataSourceNodeConfig getMySplitterDataSourceNodeConfig() {
        return mySplitterDataSourceNodeConfig;
    }

    public void healthyCheck(String sql) throws Exception {
        this.getRealDataSource().getConnection().createStatement().execute(sql);
    }

    @Override
    public String toString() {
        return "DataSourceWrapper{" +
                "nodeName='" + nodeName + '\'' +
                ", dataBaseName='" + dataBaseName + '\'' +
                '}';
    }
}
