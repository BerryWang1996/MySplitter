package com.mysplitter.reflect;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MySplitterConnectionStateReflectionTest {

    @Test
    public void shouldReplayExplicitlyClearedNullableStateOnNewConnection() throws Exception {
        Class<?> stateClass = Class.forName("com.mysplitter.MySplitterConnectionState");
        Object state = stateClass.getConstructor().newInstance();

        invoke(stateClass, state, "setCatalog", new Class<?>[]{String.class}, new Object[]{"catalog-before-clear"});
        invoke(stateClass, state, "setCatalog", new Class<?>[]{String.class}, new Object[]{null});

        Map<String, Class<?>> typeMap = new HashMap<String, Class<?>>();
        typeMap.put("json", String.class);
        invoke(stateClass, state, "setTypeMap", new Class<?>[]{Map.class}, new Object[]{typeMap});
        invoke(stateClass, state, "setTypeMap", new Class<?>[]{Map.class}, new Object[]{null});

        invoke(stateClass, state, "setSchema", new Class<?>[]{String.class}, new Object[]{"schema-before-clear"});
        invoke(stateClass, state, "setSchema", new Class<?>[]{String.class}, new Object[]{null});

        Properties clientInfo = new Properties();
        clientInfo.setProperty("trace", "enabled");
        invoke(stateClass, state, "setClientInfo", new Class<?>[]{Properties.class}, new Object[]{clientInfo});
        invoke(stateClass, state, "setClientInfo", new Class<?>[]{Properties.class}, new Object[]{new Properties()});

        RecordingConnectionHandler handler = new RecordingConnectionHandler();
        invoke(stateClass, state, "apply", new Class<?>[]{Connection.class}, new Object[]{handler.createConnection()});

        assertTrue(((Boolean) invoke(stateClass, state, "hasCatalog", new Class<?>[0], new Object[0])).booleanValue());
        assertTrue(((Boolean) invoke(stateClass, state, "hasTypeMap", new Class<?>[0], new Object[0])).booleanValue());
        assertTrue(((Boolean) invoke(stateClass, state, "hasSchema", new Class<?>[0], new Object[0])).booleanValue());
        assertTrue(((Boolean) invoke(stateClass, state, "hasClientInfo", new Class<?>[0], new Object[0])).booleanValue());
        assertNull(invoke(stateClass, state, "getCatalog", new Class<?>[0], new Object[0]));
        assertNull(invoke(stateClass, state, "getTypeMap", new Class<?>[0], new Object[0]));
        assertNull(invoke(stateClass, state, "getSchema", new Class<?>[0], new Object[0]));
        assertTrue(((Properties) invoke(stateClass, state, "getClientInfo", new Class<?>[0], new Object[0])).isEmpty());

        assertEquals(1, handler.catalogValues.size());
        assertNull(handler.catalogValues.get(0));
        assertEquals(1, handler.typeMaps.size());
        assertNull(handler.typeMaps.get(0));
        assertEquals(1, handler.schemaValues.size());
        assertNull(handler.schemaValues.get(0));
        assertEquals(1, handler.clientInfoValues.size());
        assertTrue(handler.clientInfoValues.get(0).isEmpty());
    }

    private Object invoke(Class<?> targetClass,
                          Object target,
                          String methodName,
                          Class<?>[] parameterTypes,
                          Object[] args) throws Exception {
        Method method = targetClass.getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    private static final class RecordingConnectionHandler implements InvocationHandler {

        private final List<String> catalogValues = new ArrayList<String>();

        private final List<Map<String, Class<?>>> typeMaps = new ArrayList<Map<String, Class<?>>>();

        private final List<String> schemaValues = new ArrayList<String>();

        private final List<Properties> clientInfoValues = new ArrayList<Properties>();

        private Connection createConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("setAutoCommit".equals(methodName)
                    || "setReadOnly".equals(methodName)
                    || "setTransactionIsolation".equals(methodName)
                    || "setHoldability".equals(methodName)
                    || "setNetworkTimeout".equals(methodName)) {
                return null;
            }
            if ("setCatalog".equals(methodName)) {
                catalogValues.add((String) args[0]);
                return null;
            }
            if ("setTypeMap".equals(methodName)) {
                Map<String, Class<?>> map = (Map<String, Class<?>>) args[0];
                typeMaps.add(map == null ? null : new HashMap<String, Class<?>>(map));
                return null;
            }
            if ("setSchema".equals(methodName)) {
                schemaValues.add((String) args[0]);
                return null;
            }
            if ("setClientInfo".equals(methodName) && args != null
                    && args.length == 1 && args[0] instanceof Properties) {
                Properties properties = new Properties();
                properties.putAll((Properties) args[0]);
                clientInfoValues.add(properties);
                return null;
            }
            if ("unwrap".equals(methodName)) {
                throw new SQLException("Not a wrapper.");
            }
            if ("isWrapperFor".equals(methodName)) {
                return Boolean.FALSE;
            }
            return defaultValue(method.getReturnType());
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == null || Void.TYPE.equals(returnType)) {
                return null;
            }
            if (Boolean.TYPE.equals(returnType)) {
                return Boolean.FALSE;
            }
            if (Integer.TYPE.equals(returnType)) {
                return Integer.valueOf(0);
            }
            if (Long.TYPE.equals(returnType)) {
                return Long.valueOf(0L);
            }
            if (Double.TYPE.equals(returnType)) {
                return Double.valueOf(0D);
            }
            if (Float.TYPE.equals(returnType)) {
                return Float.valueOf(0F);
            }
            if (Short.TYPE.equals(returnType)) {
                return Short.valueOf((short) 0);
            }
            if (Byte.TYPE.equals(returnType)) {
                return Byte.valueOf((byte) 0);
            }
            if (Character.TYPE.equals(returnType)) {
                return Character.valueOf((char) 0);
            }
            return null;
        }
    }
}
