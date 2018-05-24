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

}
