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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

public class MySplitterStandByExecuteHolder {

    private final CopyOnWriteArrayList<StandByInvocation> standByExecuteList =
            new CopyOnWriteArrayList<StandByInvocation>();

    public MySplitterStandByExecuteHolder(Object wrapper) {
    }

    public void standBy(String methodName, Object... params) {
        this.standByExecuteList.add(new StandByInvocation(methodName, params == null ? new Object[0] : params));
    }

    public synchronized void executeAll(Object realObject) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(realObject.getClass());
            MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
            for (StandByInvocation invocation : this.standByExecuteList) {
                Method method = findMethod(methodDescriptors, invocation);
                if (method != null) {
                    method.invoke(realObject, invocation.params);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute stand-by method.", e);
        }
    }

    public synchronized void releaseAll() {
        standByExecuteList.clear();
    }

    private Method findMethod(MethodDescriptor[] methodDescriptors, StandByInvocation invocation) {
        for (MethodDescriptor methodDescriptor : methodDescriptors) {
            Method method = methodDescriptor.getMethod();
            if (!invocation.methodName.equals(method.getName())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != invocation.params.length) {
                continue;
            }
            boolean compatible = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!isCompatible(parameterTypes[i], invocation.params[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        return null;
    }

    private boolean isCompatible(Class<?> parameterType, Object param) {
        if (param == null) {
            return !parameterType.isPrimitive();
        }
        return wrap(parameterType).isAssignableFrom(param.getClass());
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (Integer.TYPE.equals(type)) {
            return Integer.class;
        }
        if (Long.TYPE.equals(type)) {
            return Long.class;
        }
        if (Boolean.TYPE.equals(type)) {
            return Boolean.class;
        }
        if (Double.TYPE.equals(type)) {
            return Double.class;
        }
        if (Float.TYPE.equals(type)) {
            return Float.class;
        }
        if (Short.TYPE.equals(type)) {
            return Short.class;
        }
        if (Byte.TYPE.equals(type)) {
            return Byte.class;
        }
        if (Character.TYPE.equals(type)) {
            return Character.class;
        }
        return type;
    }

    private static final class StandByInvocation {

        private final String methodName;

        private final Object[] params;

        private StandByInvocation(String methodName, Object[] params) {
            this.methodName = methodName;
            this.params = params;
        }
    }
}
