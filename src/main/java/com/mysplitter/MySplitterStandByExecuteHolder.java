package com.mysplitter;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
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

    public Object object;

    public MySplitterStandByExecuteHolder(Object object) {
        this.object = object;
    }

    private CopyOnWriteArrayList<HashMap<String, Object[]>> standByExecuteList =
            new CopyOnWriteArrayList<HashMap<String, Object[]>>();

    public void standBy(String methodName, Object... params) {
        HashMap<String, Object[]> stringHashMap = new HashMap<String, Object[]>(1);
        stringHashMap.put(methodName, params);
        standByExecuteList.add(stringHashMap);
    }

    public synchronized void executeAll() {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(object.getClass());
            MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
            // 获取所有的待执行列表
            for (HashMap<String, Object[]> standByExecuteMap : standByExecuteList) {
                // 获取所有的方法
                Set<String> methods = standByExecuteMap.keySet();
                target:
                for (String method : methods) {
                    // 获取所有的方法描述
                    for (MethodDescriptor methodDescriptor : methodDescriptors) {
                        // 如果方法名相同
                        if (method.equals(methodDescriptor.getMethod().getName())) {
                            // 如果参数长度相同
                            Type[] genericParameterTypes = methodDescriptor.getMethod().getGenericParameterTypes();
                            Object[] objects = standByExecuteMap.get(method);
                            if (genericParameterTypes.length == objects.length) {
                                // 如果参数类型相同
                                for (Type genericParameterType : genericParameterTypes) {
                                    for (Object o : objects) {
                                        System.out.println(genericParameterType == o.getClass());
                                        if (genericParameterType == o.getClass()) {
                                            Method method1 = methodDescriptor.getMethod();
                                            method1.invoke(this.object, standByExecuteMap.get(method));
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
