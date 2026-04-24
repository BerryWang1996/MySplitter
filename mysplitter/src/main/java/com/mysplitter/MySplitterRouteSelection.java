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

public class MySplitterRouteSelection {

    private final MySplitterRouteKey routeKey;

    private final Connection connection;

    public MySplitterRouteSelection(MySplitterRouteKey routeKey, Connection connection) {
        this.routeKey = routeKey;
        this.connection = connection;
    }

    public MySplitterRouteKey getRouteKey() {
        return routeKey;
    }

    public Connection getConnection() {
        return connection;
    }
}
