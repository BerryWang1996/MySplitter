package com.mysplitter.test;

import com.mysplitter.selector.RandomLoadBalanceSelector;
import com.mysplitter.selector.RoundRobinLoadBalanceSelector;
import com.mysplitter.util.StringUtil;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalanceSelectorTest {

    @Test
    public void testRandomLoadBalanceSelector() throws Exception {
        long start = System.currentTimeMillis();

        int totalRun = 1700000;
        RandomLoadBalanceSelector balanceSelector = new RandomLoadBalanceSelector();
        balanceSelector.register("a", 1);
        balanceSelector.register("b", 1);
        balanceSelector.register("c", 1);
        final AtomicInteger a = new AtomicInteger(0);
        final AtomicInteger b = new AtomicInteger(0);
        final AtomicInteger c = new AtomicInteger(0);
        final Semaphore semaphore = new Semaphore(100);
        final CountDownLatch countDownLatch = new CountDownLatch(totalRun);
        ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < totalRun; i++) {
            final RandomLoadBalanceSelector balanceSelector1 = balanceSelector;
            final int index = i;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
//                    if (index == 900) {
//                        balanceSelector1.register("b", 20);
//                    }
//                    if (index == 1000) {
//                        balanceSelector1.release("b");
//                        balanceSelector1.release("a");
//                        balanceSelector1.release("c");
//                    }
//                    if (index == 1001) {
//                        balanceSelector1.register("b", 2);
//                    }
                    try {
                        semaphore.acquire();
                        String acquire = balanceSelector1.acquire();
                        if (StringUtil.isNotBlank(acquire)) {
                            switch (acquire.charAt(0)) {
                                case 'a':
                                    a.addAndGet(1);
                                    break;
                                case 'b':
                                    b.addAndGet(1);
                                    break;
                                case 'c':
                                    c.addAndGet(1);
                                    break;
                                default:
                                    System.out.println(acquire);
                                    break;
                            }
                        }
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    semaphore.release();
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("times" + (end - start));
        int total = a.get() + b.get() + c.get();
        System.out.println("total:" + total);
        System.out.println("a:" + a.get() + " " + new BigDecimal(a.get() + "").divide(new BigDecimal(total + ""), 5,
                RoundingMode.HALF_UP));
        System.out.println("b:" + b.get() + " " + new BigDecimal(b.get() + "").divide(new BigDecimal(total + ""), 5,
                RoundingMode.HALF_UP));
        System.out.println("c:" + c.get() + " " + new BigDecimal(c.get() + "").divide(new BigDecimal(total + ""), 5,
                RoundingMode.HALF_UP));
        executorService.shutdown();
    }

    @Test
    public void testRoundRobinLoadBalanceSelector() throws Exception {
        long start = System.currentTimeMillis();

        int totalRun = 1700000;
        RoundRobinLoadBalanceSelector balanceSelector = new RoundRobinLoadBalanceSelector();
        balanceSelector.register("a", 1);
        balanceSelector.register("b", 1);
        balanceSelector.register("c", 1);
        final AtomicInteger a = new AtomicInteger(0);
        final AtomicInteger b = new AtomicInteger(0);
        final AtomicInteger c = new AtomicInteger(0);
        final Semaphore semaphore = new Semaphore(100);
        final CountDownLatch countDownLatch = new CountDownLatch(totalRun);
        ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < totalRun; i++) {
            final RoundRobinLoadBalanceSelector balanceSelector1 = balanceSelector;
            final int index = i;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
//                    if (index == 900) {
//                        balanceSelector1.register("b", 20);
//                    }
//                    if (index == 1000) {
//                        balanceSelector1.release("b");
//                        balanceSelector1.release("a");
//                        balanceSelector1.release("c");
//                    }
//                    if (index == 1001) {
//                        balanceSelector1.register("b", 2);
//                    }
                    try {
                        semaphore.acquire();
                        String acquire = balanceSelector1.acquire();
                        if (StringUtil.isNotBlank(acquire)) {
                            switch (acquire.charAt(0)) {
                                case 'a':
                                    a.addAndGet(1);
                                    break;
                                case 'b':
                                    b.addAndGet(1);
                                    break;
                                case 'c':
                                    c.addAndGet(1);
                                    break;
                                default:
                                    System.out.println(acquire);
                                    break;
                            }
                        }
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    semaphore.release();
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("times" + (end - start));
        int total = a.get() + b.get() + c.get();
        System.out.println("total:" + total);
        System.out.println("a:" + a.get() + " " + new BigDecimal(a.get() + "").divide(new BigDecimal(total + ""), 5,
                RoundingMode.HALF_UP));
        System.out.println("b:" + b.get() + " " + new BigDecimal(b.get() + "").divide(new BigDecimal(total + ""), 5,
                RoundingMode.HALF_UP));
        System.out.println("c:" + c.get() + " " + new BigDecimal(c.get() + "").divide(new BigDecimal(total + ""), 5,
                RoundingMode.HALF_UP));
        executorService.shutdown();
    }

}
