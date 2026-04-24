package com.mysplitter.test;

import com.mysplitter.DataSourceWrapper;
import com.mysplitter.MySplitterDataSourceGroup;
import com.mysplitter.MySplitterDataSourceHealthManager;
import com.mysplitter.advise.DataSourceIllAlerterAdvise;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterLoadBalanceConfig;
import com.mysplitter.selector.NoLoadBalanceSelector;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MySplitterDataSourceHealthManagerTest {

    @Test
    public void shouldKeepNodeIllUntilLatestFailureTimeoutExpires() throws Exception {
        CountingAlerter alerter = new CountingAlerter();
        MySplitterDataSourceHealthManager healthManager = new MySplitterDataSourceHealthManager(alerter);
        try {
            DataSourceWrapper dataSourceWrapper = createDataSourceWrapper();
            MySplitterDataSourceGroup group = new MySplitterDataSourceGroup("database-main:writers",
                    "database-main", "writers", new NoLoadBalanceSelector<DataSourceWrapper>());
            group.register(dataSourceWrapper, 1);

            healthManager.markIll(group, dataSourceWrapper, new RuntimeException("first failure"));
            Thread.sleep(2000L);
            healthManager.markIll(group, dataSourceWrapper, new RuntimeException("second failure"));

            assertFalse(healthManager.isHealthy(group, dataSourceWrapper));
            assertEquals(1, alerter.alertCount.get());

            long recoveredAfterMillis = waitForHealthyState(healthManager, group, dataSourceWrapper, 5000L);
            assertTrue(recoveredAfterMillis >= 3000L);
            assertTrue(healthManager.isHealthy(group, dataSourceWrapper));
        } finally {
            healthManager.close();
        }
    }

    private DataSourceWrapper createDataSourceWrapper() {
        MySplitterDataSourceNodeConfig nodeConfig = new MySplitterDataSourceNodeConfig();
        nodeConfig.setWeight(1);
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("url", "jdbc:mysql://localhost:3306/test");
        nodeConfig.setConfiguration(configuration);

        MySplitterLoadBalanceConfig loadBalanceConfig = new MySplitterLoadBalanceConfig();
        loadBalanceConfig.setFailTimeout("4s");

        return new DataSourceWrapper("writer-node", "database-main", nodeConfig, loadBalanceConfig);
    }

    private long waitForHealthyState(MySplitterDataSourceHealthManager healthManager,
                                     MySplitterDataSourceGroup group,
                                     DataSourceWrapper dataSourceWrapper,
                                     long timeoutMillis) throws Exception {
        long start = System.currentTimeMillis();
        long deadline = start + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (healthManager.isHealthy(group, dataSourceWrapper)) {
                return System.currentTimeMillis() - start;
            }
            Thread.sleep(25L);
        }
        assertTrue(healthManager.isHealthy(group, dataSourceWrapper));
        return System.currentTimeMillis() - start;
    }

    private static final class CountingAlerter implements DataSourceIllAlerterAdvise {

        private final AtomicInteger alertCount = new AtomicInteger(0);

        @Override
        public void alert(String databaseName, String nodeName, Exception e) {
            alertCount.incrementAndGet();
        }
    }
}
