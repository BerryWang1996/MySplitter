package com.mysplitter.util;

import java.lang.reflect.Constructor;

/**
 * @Author: wangbor
 * @Date: 2018/5/23 10:53
 */
public class ClassLoaderUtil {

    private ClassLoaderUtil() {
    }

    public static <T> T getInstance(String name, Class<T> clz) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Constructor constructor = null;
        Class aClass = classLoader.loadClass(name);
        constructor = aClass.getConstructor();
        return (T) constructor.newInstance();
    }

    public static boolean isSameType(Class<?> aClass, Class<?> bClass) {
        aClass = doPackage(aClass);
        bClass = doPackage(bClass);
        return aClass.getName().equals(bClass.getName());
    }

    private static Class<?> doPackage(Class<?> aClass) {
        if ("byte".equals(aClass.getName())) {
            aClass = Byte.class;
        }
        if ("int".equals(aClass.getName())) {
            aClass = Integer.class;
        }
        if ("short".equals(aClass.getName())) {
            aClass = Short.class;
        }
        if ("long".equals(aClass.getName())) {
            aClass = Long.class;
        }
        if ("double".equals(aClass.getName())) {
            aClass = Double.class;
        }
        if ("float".equals(aClass.getName())) {
            aClass = Float.class;
        }
        if ("boolean".equals(aClass.getName())) {
            aClass = Boolean.class;
        }
        if ("char".equals(aClass.getName())) {
            aClass = Character.class;
        }
        return aClass;
    }

}
