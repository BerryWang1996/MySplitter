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

package com.mysplitter.util;

import java.lang.reflect.Constructor;

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
