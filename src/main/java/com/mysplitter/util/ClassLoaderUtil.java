package com.mysplitter.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @Author: wangbor
 * @Date: 2018/5/23 10:53
 */
public class ClassLoaderUtil {

    private ClassLoaderUtil() {
    }

    public static <T> T getInstance(String name, Class<T> clz) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Constructor constructor = null;
        try {
            Class aClass = classLoader.loadClass(name);
            constructor = aClass.getConstructor();
            return (T) constructor.newInstance();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

}
