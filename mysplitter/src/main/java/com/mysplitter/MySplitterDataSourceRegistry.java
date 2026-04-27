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

import com.mysplitter.selector.LoadBalanceSelector;
import com.mysplitter.transaction.XaResourceRegistry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MySplitterDataSourceRegistry {

    private final Map<String, MySplitterDataSourceGroup> groups =
            new ConcurrentHashMap<String, MySplitterDataSourceGroup>();

    public MySplitterDataSourceGroup createGroup(String databaseName,
                                                 String nodeGroup,
                                                 LoadBalanceSelector<DataSourceWrapper> selector) {
        String selectorName = generateSelectorName(databaseName, nodeGroup);
        MySplitterDataSourceGroup group = new MySplitterDataSourceGroup(selectorName, databaseName, nodeGroup, selector);
        groups.put(selectorName, group);
        return group;
    }

    public MySplitterDataSourceGroup getGroup(String databaseName, String nodeGroup) {
        MySplitterDataSourceGroup group = groups.get(generateSelectorName(databaseName, nodeGroup));
        if (group == null && !"integrates".equals(nodeGroup)) {
            group = groups.get(generateSelectorName(databaseName, "integrates"));
        }
        return group;
    }

    public List<MySplitterDataSourceGroup> listGroups() {
        return new ArrayList<MySplitterDataSourceGroup>(groups.values());
    }

    public List<DataSourceWrapper> listAllNodes() {
        Set<DataSourceWrapper> wrappers = new LinkedHashSet<DataSourceWrapper>();
        for (MySplitterDataSourceGroup group : groups.values()) {
            wrappers.addAll(group.listAll());
        }
        return new ArrayList<DataSourceWrapper>(wrappers);
    }

    public XaResourceRegistry createXaResourceRegistry() throws SQLException {
        XaResourceRegistry xaResourceRegistry = new XaResourceRegistry();
        for (DataSourceWrapper wrapper : listAllNodes()) {
            xaResourceRegistry.register(wrapper);
        }
        return xaResourceRegistry;
    }

    public void clear() {
        groups.clear();
    }

    private String generateSelectorName(String databaseName, String nodeGroup) {
        return databaseName + ":" + nodeGroup;
    }
}
