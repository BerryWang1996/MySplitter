package com.mysplitter.test;

import com.mysplitter.selector.RandomLoadBalanceSelector;
import com.mysplitter.selector.RoundRobinLoadBalanceSelector;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoadBalanceSelectorTest {

    @Test
    public void shouldKeepUniqueNodesInRandomSelectorRegistry() {
        RandomLoadBalanceSelector<String> selector = new RandomLoadBalanceSelector<String>();

        selector.register("writer-a", 1);
        selector.register("writer-a", 5);
        selector.register("writer-b", 1);

        assertEquals(Arrays.asList("writer-a", "writer-b"), selector.listAll());
    }

    @Test
    public void shouldPreferHigherWeightNodeInRandomSelector() {
        RandomLoadBalanceSelector<String> selector = new RandomLoadBalanceSelector<String>();
        selector.register("writer-a", 5);
        selector.register("writer-b", 1);

        int writerACount = 0;
        int writerBCount = 0;
        for (int i = 0; i < 1200; i++) {
            String selected = selector.acquire();
            if ("writer-a".equals(selected)) {
                writerACount++;
            } else if ("writer-b".equals(selected)) {
                writerBCount++;
            }
        }

        assertTrue(writerACount > writerBCount);
        assertTrue(writerACount >= 800);
    }

    @Test
    public void shouldCycleNodesInRoundRobinSelector() {
        RoundRobinLoadBalanceSelector<String> selector = new RoundRobinLoadBalanceSelector<String>();
        selector.register("reader-a", 1);
        selector.register("reader-b", 1);
        selector.register("reader-c", 1);

        assertEquals("reader-a", selector.acquire());
        assertEquals("reader-b", selector.acquire());
        assertEquals("reader-c", selector.acquire());
        assertEquals("reader-a", selector.acquire());
        assertEquals("reader-b", selector.acquire());
        assertEquals("reader-c", selector.acquire());
    }
}
