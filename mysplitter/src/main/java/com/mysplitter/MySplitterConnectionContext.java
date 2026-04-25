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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MySplitterConnectionContext {

    private static final Object ADMINISTRATIVE_CONNECTION_KEY = new Object();

    private final MySplitterConnectionState connectionState = new MySplitterConnectionState();

    private final Map<Object, Connection> connections = new LinkedHashMap<Object, Connection>();

    private final Map<String, MySplitterRouteKey> pinnedRouteKeys = new HashMap<String, MySplitterRouteKey>();

    private Object currentConnectionKey;

    public MySplitterConnectionState getConnectionState() {
        return connectionState;
    }

    public synchronized boolean isTransactionActive() {
        return !connectionState.isAutoCommit();
    }

    public synchronized Connection getConnection(MySplitterRouteKey routeKey) {
        Connection connection = connections.get(routeKey);
        if (connection != null) {
            currentConnectionKey = routeKey;
        }
        return connection;
    }

    public synchronized void registerConnection(MySplitterRouteKey routeKey, Connection connection) {
        if (!connections.containsKey(routeKey)) {
            connections.put(routeKey, connection);
        }
        currentConnectionKey = routeKey;
    }

    public synchronized void assertCanOpenRouteInTransaction(MySplitterRouteKey routeKey) throws SQLException {
        if (!isTransactionActive()) {
            return;
        }
        for (Object connectionKey : connections.keySet()) {
            if (!routeKey.equals(connectionKey)) {
                throw multipleConnectionTransactionException(describeConnectionKey(connectionKey),
                        describeConnectionKey(routeKey));
            }
        }
    }

    public synchronized void removeConnection(MySplitterRouteKey routeKey) {
        connections.remove(routeKey);
        if (routeKey.equals(currentConnectionKey)) {
            currentConnectionKey = null;
        }
    }

    public synchronized Connection getAdministrativeConnection() {
        Connection connection = connections.get(ADMINISTRATIVE_CONNECTION_KEY);
        if (connection != null) {
            currentConnectionKey = ADMINISTRATIVE_CONNECTION_KEY;
        }
        return connection;
    }

    public synchronized void registerAdministrativeConnection(Connection connection) {
        if (!connections.containsKey(ADMINISTRATIVE_CONNECTION_KEY)) {
            connections.put(ADMINISTRATIVE_CONNECTION_KEY, connection);
        }
        currentConnectionKey = ADMINISTRATIVE_CONNECTION_KEY;
    }

    public synchronized void assertCanOpenAdministrativeConnectionInTransaction() throws SQLException {
        if (!isTransactionActive()) {
            return;
        }
        for (Object connectionKey : connections.keySet()) {
            if (!ADMINISTRATIVE_CONNECTION_KEY.equals(connectionKey)) {
                throw multipleConnectionTransactionException(describeConnectionKey(connectionKey),
                        describeConnectionKey(ADMINISTRATIVE_CONNECTION_KEY));
            }
        }
    }

    public synchronized Connection getCurrentConnection() {
        if (currentConnectionKey != null) {
            return connections.get(currentConnectionKey);
        }
        Connection current = null;
        for (Connection connection : connections.values()) {
            current = connection;
        }
        return current;
    }

    public synchronized List<Connection> listAllConnections() {
        return new ArrayList<Connection>(connections.values());
    }

    public synchronized MySplitterRouteKey getPinnedRoute(String databaseName) {
        return pinnedRouteKeys.get(databaseName);
    }

    public synchronized void pinRoute(String databaseName, MySplitterRouteKey routeKey) {
        pinnedRouteKeys.put(databaseName, routeKey);
    }

    public synchronized void clearPinnedRoute(String databaseName) {
        pinnedRouteKeys.remove(databaseName);
    }

    public synchronized void clearPinnedRoutes() {
        pinnedRouteKeys.clear();
    }

    public synchronized void clear() {
        connections.clear();
        pinnedRouteKeys.clear();
        currentConnectionKey = null;
    }

    private SQLException multipleConnectionTransactionException(String existingConnectionKey,
                                                                String requestedConnectionKey) {
        return new SQLException("MySplitter local transactions do not support multiple physical connections. " +
                "Existing connection is " + existingConnectionKey + ", requested connection is " +
                requestedConnectionKey + ". Split the work into separate transactions or use XA/Saga/compensation " +
                "for distributed transaction semantics.");
    }

    private String describeConnectionKey(Object connectionKey) {
        if (ADMINISTRATIVE_CONNECTION_KEY.equals(connectionKey)) {
            return "administrative";
        }
        return String.valueOf(connectionKey);
    }
}
