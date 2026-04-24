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

package com.mysplitter.spring.boot.autoconfigure;

import org.junit.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MySplitterAutoConfigureTest {

    private static final String DEFAULT_CONFIGURATION_FILE = "classpath:mysplitter.yml";

    private static final String PROPERTIES_CLASS_NAME =
            "com.mysplitter.spring.boot.autoconfigure.MySplitterConfigureProperties";

    private static final String AUTO_CONFIGURE_CLASS_NAME =
            "com.mysplitter.spring.boot.autoconfigure.MySplitterAutoConfigure";

    @Test
    public void shouldNormalizeBlankConfigurationFileToDefaultLocation() {
        Object properties = newProperties();

        invoke(properties, "setConfigurationFile", new Class<?>[]{String.class}, "  ");

        assertEquals(DEFAULT_CONFIGURATION_FILE, invoke(properties, "getConfigurationFile"));
    }

    @Test
    public void shouldCreateDataSourceFromClasspathConfiguration() throws Exception {
        Object properties = newProperties();
        invoke(properties, "setConfigurationFile",
                new Class<?>[]{String.class}, "classpath:starter-test-mysplitter.yml");
        Object autoConfigure = newAutoConfigure(properties);

        Object dataSource = invoke(autoConfigure, "mySplitterDataSource");
        Object rootConfig = invoke(dataSource, "getMySplitterConfig");
        Object mySplitterConfig = invoke(rootConfig, "getMysplitter");
        Object commonConfig = invoke(mySplitterConfig, "getCommon");

        assertNotNull(rootConfig);
        assertNotNull(mySplitterConfig);
        assertNotNull(commonConfig);
        assertEquals("com.zaxxer.hikari.HikariDataSource",
                invoke(commonConfig, "getDataSourceClass"));
    }

    @Test
    public void shouldCreateDataSourceFromFileConfiguration() throws Exception {
        Path tempFile = Files.createTempFile("mysplitter-starter", ".yml");
        tempFile.toFile().deleteOnExit();
        Files.write(tempFile, (
                "mysplitter:\n" +
                        "  readAndWriteParser: com.mysplitter.DefaultReadAndWriteParser\n" +
                        "  common:\n" +
                        "    dataSourceClass: com.zaxxer.hikari.HikariDataSource\n")
                .getBytes(StandardCharsets.UTF_8));
        Object properties = newProperties();
        invoke(properties, "setConfigurationFile", new Class<?>[]{String.class}, tempFile.toUri().toString());
        Object autoConfigure = newAutoConfigure(properties);
        Object dataSource = invoke(autoConfigure, "mySplitterDataSource");
        Object rootConfig = invoke(dataSource, "getMySplitterConfig");
        Object mySplitterConfig = invoke(rootConfig, "getMysplitter");
        Object commonConfig = invoke(mySplitterConfig, "getCommon");

        assertNotNull(rootConfig);
        assertEquals("com.mysplitter.DefaultReadAndWriteParser",
                invoke(mySplitterConfig, "getReadAndWriteParser"));
        assertEquals("com.zaxxer.hikari.HikariDataSource",
                invoke(commonConfig, "getDataSourceClass"));
    }

    @Test
    public void shouldFailWhenConfigurationResourceIsMissing() throws Exception {
        Object properties = newProperties();
        invoke(properties, "setConfigurationFile",
                new Class<?>[]{String.class}, "classpath:missing-mysplitter.yml");
        Object autoConfigure = newAutoConfigure(properties);

        try {
            invoke(autoConfigure, "mySplitterDataSource");
            fail("Expected FileNotFoundException");
        } catch (RuntimeException e) {
            Throwable cause = rootCause(e);
            assertTrue(cause instanceof FileNotFoundException);
            assertTrue(cause.getMessage().contains("missing-mysplitter.yml"));
        }
    }

    private Object newProperties() {
        try {
            return Class.forName(PROPERTIES_CLASS_NAME).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object newAutoConfigure(Object properties) {
        try {
            Class<?> propertiesClass = Class.forName(PROPERTIES_CLASS_NAME);
            Class<?> autoConfigureClass = Class.forName(AUTO_CONFIGURE_CLASS_NAME);
            Constructor<?> constructor = autoConfigureClass
                    .getDeclaredConstructor(propertiesClass, org.springframework.core.io.ResourceLoader.class);
            return constructor.newInstance(properties, new DefaultResourceLoader());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
