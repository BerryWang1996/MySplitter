package com.mysplitter;

import com.mysplitter.util.ClassLoaderUtil;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 待执行方法处理器
 *
 * @Author: 王伯瑞
 * @Date: 2018/6/4 9:27
 */
public class MySplitterStandByExecuteHolder {

    public Object wrapper;

    public MySplitterStandByExecuteHolder(Object wrapper) {
        this.wrapper = wrapper;
    }

    private CopyOnWriteArrayList<HashMap<String, Object[]>> standByExecuteList =
            new CopyOnWriteArrayList<HashMap<String, Object[]>>();

    public void standBy(String methodName, Object... params) {
        HashMap<String, Object[]> stringHashMap = new HashMap<String, Object[]>(1);
        stringHashMap.put(methodName, params);
        this.standByExecuteList.add(stringHashMap);
    }

    public synchronized void executeAll(Object realObject) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(realObject.getClass());
            MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
            // 获取所有的待执行列表
            for (HashMap<String, Object[]> standByExecuteMap : this.standByExecuteList) {
                // 获取所有的方法
                Set<String> methods = standByExecuteMap.keySet();
                target:
                for (String method : methods) {
                    // 获取所有的方法描述
                    for (MethodDescriptor methodDescriptor : methodDescriptors) {
                        // 如果方法名相同
                        if (method.equals(methodDescriptor.getMethod().getName())) {
                            // 如果参数长度相同
                            Class<?>[] genericParameterTypes = methodDescriptor.getMethod().getParameterTypes();
                            Object[] objects = standByExecuteMap.get(method);
                            if (genericParameterTypes.length == objects.length) {
                                // 如果参数类型相同
                                for (Class genericParameterType : genericParameterTypes) {
                                    for (Object o : objects) {
                                        if (ClassLoaderUtil.isSameType(genericParameterType, o.getClass())) {
                                            Method method1 = methodDescriptor.getMethod();
                                            method1.invoke(realObject, standByExecuteMap.get(method));
                                            break target;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public synchronized void releaseAll() {
        standByExecuteList.clear();
    }

}
