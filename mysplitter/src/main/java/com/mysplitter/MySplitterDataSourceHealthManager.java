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

import com.mysplitter.advise.DataSourceIllAlerterAdvise;
import com.mysplitter.util.StringUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class MySplitterDataSourceHealthManager {

    private final DataSourceIllAlerterAdvise dataSourceIllAlerter;

    private final ScheduledThreadPoolExecutor failTimeoutExecutor;

    private final AtomicLong failureVersionSequence = new AtomicLong(0L);

    private final ConcurrentMap<String, ConcurrentMap<String, Long>> illNodeVersionsBySelector =
            new ConcurrentHashMap<String, ConcurrentMap<String, Long>>();

    public MySplitterDataSourceHealthManager(DataSourceIllAlerterAdvise dataSourceIllAlerter) {
        this.dataSourceIllAlerter = dataSourceIllAlerter;
        this.failTimeoutExecutor = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                new DaemonThreadFactory("mysplitter fail timeout"));
    }

    public List<DataSourceWrapper> getHealthyNodes(MySplitterDataSourceGroup group) {
        List<DataSourceWrapper> healthy = new ArrayList<DataSourceWrapper>();
        for (DataSourceWrapper wrapper : group.listAll()) {
            if (isHealthy(group, wrapper)) {
                healthy.add(wrapper);
            }
        }
        return healthy;
    }

    public List<DataSourceWrapper> getIllNodes(MySplitterDataSourceGroup group) {
        List<DataSourceWrapper> ill = new ArrayList<DataSourceWrapper>();
        for (DataSourceWrapper wrapper : group.listAll()) {
            if (!isHealthy(group, wrapper)) {
                ill.add(wrapper);
            }
        }
        return ill;
    }

    public boolean isHealthy(MySplitterDataSourceGroup group, DataSourceWrapper wrapper) {
        ConcurrentMap<String, Long> illNodeVersions = illNodeVersionsBySelector.get(group.getSelectorName());
        return illNodeVersions == null || !illNodeVersions.containsKey(wrapper.getNodeName());
    }

    public void markIll(final MySplitterDataSourceGroup group,
                        final DataSourceWrapper wrapper,
                        Exception exception) {
        ConcurrentMap<String, Long> illNodeVersions = getIllVersions(group.getSelectorName());
        long failureVersion = failureVersionSequence.incrementAndGet();
        Long previousVersion = illNodeVersions.put(wrapper.getNodeName(), Long.valueOf(failureVersion));
        if (previousVersion == null) {
            dataSourceIllAlerter.alert(wrapper.getDataBaseName(), wrapper.getNodeName(), exception);
        }
        scheduleRecover(group, wrapper, failureVersion);
    }

    public void markHealthy(MySplitterDataSourceGroup group, DataSourceWrapper wrapper) {
        ConcurrentMap<String, Long> illNodeVersions = illNodeVersionsBySelector.get(group.getSelectorName());
        if (illNodeVersions == null) {
            return;
        }
        illNodeVersions.remove(wrapper.getNodeName());
        if (illNodeVersions.isEmpty()) {
            illNodeVersionsBySelector.remove(group.getSelectorName(), illNodeVersions);
        }
    }

    public boolean hasIllNodes(MySplitterDataSourceGroup group) {
        return !getIllNodes(group).isEmpty();
    }

    public void close() {
        failTimeoutExecutor.shutdownNow();
        illNodeVersionsBySelector.clear();
    }

    public Map<String, Object> getStatus(MySplitterDataSourceRegistry registry) {
        Map<String, Object> status = new TreeMap<String, Object>();
        Map<String, Object> healthy = new TreeMap<String, Object>();
        Map<String, Object> ill = new TreeMap<String, Object>();
        for (MySplitterDataSourceGroup group : registry.listGroups()) {
            healthy.put(group.getSelectorName(), toNodeNames(getHealthyNodes(group)));
            ill.put(group.getSelectorName(), toNodeNames(getIllNodes(group)));
        }
        status.put("healthy", healthy);
        status.put("ill", ill);
        return status;
    }

    private ConcurrentMap<String, Long> getIllVersions(String selectorName) {
        ConcurrentMap<String, Long> illNodeVersions = illNodeVersionsBySelector.get(selectorName);
        if (illNodeVersions == null) {
            ConcurrentMap<String, Long> newMap = new ConcurrentHashMap<String, Long>();
            ConcurrentMap<String, Long> existing = illNodeVersionsBySelector.putIfAbsent(selectorName, newMap);
            illNodeVersions = existing == null ? newMap : existing;
        }
        return illNodeVersions;
    }

    private void scheduleRecover(final MySplitterDataSourceGroup group,
                                 final DataSourceWrapper wrapper,
                                 final long failureVersion) {
        final String time;
        if (wrapper.getLoadBalanceConfig() == null
                || StringUtil.isBlank(wrapper.getLoadBalanceConfig().getFailTimeout())) {
            time = "30s";
        } else {
            time = wrapper.getLoadBalanceConfig().getFailTimeout();
        }
        failTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                ConcurrentMap<String, Long> illNodeVersions = illNodeVersionsBySelector.get(group.getSelectorName());
                if (illNodeVersions == null) {
                    return;
                }
                Long currentVersion = illNodeVersions.get(wrapper.getNodeName());
                if (currentVersion != null && currentVersion.longValue() == failureVersion) {
                    markHealthy(group, wrapper);
                }
            }
        }, parseTimePeriod(time), parseTimeTimeUnit(time));
    }

    private List<String> toNodeNames(List<DataSourceWrapper> wrappers) {
        LinkedHashSet<String> nodeNames = new LinkedHashSet<String>();
        for (DataSourceWrapper wrapper : wrappers) {
            nodeNames.add(wrapper.getNodeName());
        }
        return new ArrayList<String>(nodeNames);
    }

    private Integer parseTimePeriod(String time) {
        return Integer.parseInt(time.substring(0, time.length() - 1));
    }

    private TimeUnit parseTimeTimeUnit(String time) {
        String suffix = time.substring(time.length() - 1);
        if ("s".equalsIgnoreCase(suffix)) {
            return TimeUnit.SECONDS;
        }
        if ("m".equalsIgnoreCase(suffix)) {
            return TimeUnit.MINUTES;
        }
        if ("h".equalsIgnoreCase(suffix)) {
            return TimeUnit.HOURS;
        }
        return TimeUnit.SECONDS;
    }
}
