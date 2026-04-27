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

package com.mysplitter.transaction;

import com.mysplitter.DataSourceWrapper;
import com.mysplitter.util.StringUtil;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XaResourceRegistry {

    private final Map<String, XaDataSourceAdapter> adapters =
            new ConcurrentHashMap<String, XaDataSourceAdapter>();

    public void register(XaDataSourceAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("MySplitter XA adapter is null.");
        }
        adapters.put(adapter.getResourceId(), adapter);
    }

    public void register(DataSourceWrapper dataSourceWrapper) throws SQLException {
        if (dataSourceWrapper == null) {
            throw new IllegalArgumentException("MySplitter datasource wrapper is null.");
        }
        XaResourceDescriptor descriptor = dataSourceWrapper.getXaResourceDescriptor();
        if (descriptor == null || !descriptor.isXaCapable() || dataSourceWrapper.getXaDataSourceAdapter() == null) {
            String reason = descriptor == null ? "XA resource descriptor is not initialized." :
                    descriptor.getUnavailableReason();
            throw new SQLFeatureNotSupportedException("Datasource node " + dataSourceWrapper.getNodeName() +
                    " in database " + dataSourceWrapper.getDataBaseName() + " is not XA capable. " + reason);
        }
        register(dataSourceWrapper.getXaDataSourceAdapter());
    }

    public boolean registerIfXaCapable(DataSourceWrapper dataSourceWrapper) throws SQLException {
        if (dataSourceWrapper == null || !dataSourceWrapper.isXaCapable()) {
            return false;
        }
        register(dataSourceWrapper);
        return true;
    }

    public XaRecoveryResource openRecoveryResource(String resourceId) throws SQLException {
        return openRecoveryResource(resourceId, null, null);
    }

    public XaRecoveryResource openRecoveryResource(String resourceId,
                                                  String username,
                                                  String password) throws SQLException {
        if (StringUtil.isBlank(resourceId)) {
            throw new IllegalArgumentException("MySplitter XA recovery resourceId is empty.");
        }
        XaDataSourceAdapter adapter = adapters.get(resourceId);
        if (adapter == null) {
            throw new SQLException("MySplitter XA recovery resource " + resourceId + " is not registered.");
        }
        return adapter.openRecoveryResource(username, password);
    }

    public int size() {
        return adapters.size();
    }
}
