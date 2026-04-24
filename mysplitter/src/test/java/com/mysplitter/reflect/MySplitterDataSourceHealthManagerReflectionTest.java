package com.mysplitter.reflect;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MySplitterDataSourceHealthManagerReflectionTest {

    @Test
    public void shouldNotClearNewFailureWhenStaleRecoveryTaskFinishesLater() throws Exception {
        AlerterHandler alerterHandler = new AlerterHandler();
        CapturingScheduledExecutor executor = new CapturingScheduledExecutor();
        Object healthManager = createHealthManager(alerterHandler, executor);
        Class<?> healthManagerClass = healthManager.getClass();
        Object group = createGroup();
        Class<?> groupClass = group.getClass();
        Object wrapper = createDataSourceWrapper();
        Class<?> wrapperClass = wrapper.getClass();

        invoke(groupClass, group, "register",
                new Class<?>[]{wrapperClass, Integer.TYPE}, new Object[]{wrapper, Integer.valueOf(1)});

        invoke(healthManagerClass, healthManager, "markIll",
                new Class<?>[]{groupClass, wrapperClass, Exception.class},
                new Object[]{group, wrapper, new RuntimeException("first failure")});

        BlockingConcurrentMap blockingMap = replaceSelectorMapWithBlockingMap(healthManager, group, wrapper);
        Runnable firstRecovery = executor.pollTask();
        assertNotNull(firstRecovery);

        Thread recoveryThread = new Thread(firstRecovery, "stale-recovery");
        recoveryThread.start();
        assertTrue(blockingMap.awaitRemoveAttempt(3, TimeUnit.SECONDS));

        invoke(healthManagerClass, healthManager, "markIll",
                new Class<?>[]{groupClass, wrapperClass, Exception.class},
                new Object[]{group, wrapper, new RuntimeException("second failure")});
        assertFalse(((Boolean) invoke(healthManagerClass, healthManager, "isHealthy",
                new Class<?>[]{groupClass, wrapperClass}, new Object[]{group, wrapper})).booleanValue());
        assertEquals(1, alerterHandler.alertCount.get());

        blockingMap.releaseRemoveAttempt();
        recoveryThread.join(3000L);

        assertFalse("Stale recovery must not remove the newer failure state.",
                ((Boolean) invoke(healthManagerClass, healthManager, "isHealthy",
                        new Class<?>[]{groupClass, wrapperClass}, new Object[]{group, wrapper})).booleanValue());

        Runnable secondRecovery = executor.pollTask();
        assertNotNull(secondRecovery);
        secondRecovery.run();

        assertTrue("Latest recovery should still be able to heal the node.",
                ((Boolean) invoke(healthManagerClass, healthManager, "isHealthy",
                        new Class<?>[]{groupClass, wrapperClass}, new Object[]{group, wrapper})).booleanValue());

        invoke(healthManagerClass, healthManager, "close", new Class<?>[0], new Object[0]);
    }

    @Test
    public void shouldAlertOnlyOnceWhenManyThreadsMarkSameNodeIll() throws Exception {
        final AlerterHandler alerterHandler = new AlerterHandler();
        final CapturingScheduledExecutor executor = new CapturingScheduledExecutor();
        final Object healthManager = createHealthManager(alerterHandler, executor);
        final Class<?> healthManagerClass = healthManager.getClass();
        final Object group = createGroup();
        final Class<?> groupClass = group.getClass();
        final Object wrapper = createDataSourceWrapper();
        final Class<?> wrapperClass = wrapper.getClass();

        invoke(groupClass, group, "register",
                new Class<?>[]{wrapperClass, Integer.TYPE}, new Object[]{wrapper, Integer.valueOf(1)});

        int concurrency = 8;
        final CountDownLatch ready = new CountDownLatch(concurrency);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(concurrency);
        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    ready.countDown();
                    try {
                        start.await();
                        invoke(healthManagerClass, healthManager, "markIll",
                                new Class<?>[]{groupClass, wrapperClass, Exception.class},
                                new Object[]{group, wrapper, new RuntimeException("failure-" + index)});
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    } finally {
                        done.countDown();
                    }
                }
            }, "mark-ill-" + i);
            thread.start();
        }

        assertTrue(ready.await(3, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));

        assertFalse(((Boolean) invoke(healthManagerClass, healthManager, "isHealthy",
                new Class<?>[]{groupClass, wrapperClass}, new Object[]{group, wrapper})).booleanValue());
        assertEquals(1, alerterHandler.alertCount.get());

        Runnable recoveryTask;
        while ((recoveryTask = executor.pollTask()) != null) {
            recoveryTask.run();
        }

        assertTrue("The newest recovery task should restore the node after stale tasks no-op.",
                ((Boolean) invoke(healthManagerClass, healthManager, "isHealthy",
                        new Class<?>[]{groupClass, wrapperClass}, new Object[]{group, wrapper})).booleanValue());

        invoke(healthManagerClass, healthManager, "close", new Class<?>[0], new Object[0]);
    }

    @Test
    public void shouldOnlyClearIllStateWhenHealthyMatchUsesCurrentVersion() throws Exception {
        AlerterHandler alerterHandler = new AlerterHandler();
        CapturingScheduledExecutor executor = new CapturingScheduledExecutor();
        Object healthManager = createHealthManager(alerterHandler, executor);
        Class<?> healthManagerClass = healthManager.getClass();
        Object group = createGroup();
        Class<?> groupClass = group.getClass();
        Object wrapper = createDataSourceWrapper();
        Class<?> wrapperClass = wrapper.getClass();

        invoke(groupClass, group, "register",
                new Class<?>[]{wrapperClass, Integer.TYPE}, new Object[]{wrapper, Integer.valueOf(1)});

        invoke(healthManagerClass, healthManager, "markIll",
                new Class<?>[]{groupClass, wrapperClass, Exception.class},
                new Object[]{group, wrapper, new RuntimeException("first failure")});
        Long firstVersion = (Long) invokeDeclared(healthManagerClass, healthManager, "getIllVersion",
                new Class<?>[]{groupClass, wrapperClass}, new Object[]{group, wrapper});

        invoke(healthManagerClass, healthManager, "markIll",
                new Class<?>[]{groupClass, wrapperClass, Exception.class},
                new Object[]{group, wrapper, new RuntimeException("second failure")});
        Long secondVersion = (Long) invokeDeclared(healthManagerClass, healthManager, "getIllVersion",
                new Class<?>[]{groupClass, wrapperClass}, new Object[]{group, wrapper});

        assertFalse(((Boolean) invokeDeclared(healthManagerClass, healthManager,
                "markHealthyIfCurrentVersionMatches",
                new Class<?>[]{groupClass, wrapperClass, Long.class},
                new Object[]{group, wrapper, firstVersion})).booleanValue());
        assertFalse(((Boolean) invoke(healthManagerClass, healthManager, "isHealthy",
                new Class<?>[]{groupClass, wrapperClass}, new Object[]{group, wrapper})).booleanValue());

        assertTrue(((Boolean) invokeDeclared(healthManagerClass, healthManager,
                "markHealthyIfCurrentVersionMatches",
                new Class<?>[]{groupClass, wrapperClass, Long.class},
                new Object[]{group, wrapper, secondVersion})).booleanValue());
        assertTrue(((Boolean) invoke(healthManagerClass, healthManager, "isHealthy",
                new Class<?>[]{groupClass, wrapperClass}, new Object[]{group, wrapper})).booleanValue());

        invoke(healthManagerClass, healthManager, "close", new Class<?>[0], new Object[0]);
    }

    private Object createHealthManager(AlerterHandler alerterHandler, ScheduledExecutorService executor)
            throws Exception {
        Class<?> healthManagerClass = Class.forName("com.mysplitter.MySplitterDataSourceHealthManager");
        Class<?> alerterClass = Class.forName("com.mysplitter.advise.DataSourceIllAlerterAdvise");
        Constructor<?> constructor = healthManagerClass.getDeclaredConstructor(alerterClass, ScheduledExecutorService.class);
        constructor.setAccessible(true);
        return constructor.newInstance(alerterHandler.createProxy(alerterClass), executor);
    }

    private Object createGroup() throws Exception {
        Class<?> selectorInterface = Class.forName("com.mysplitter.selector.LoadBalanceSelector");
        Class<?> selectorClass = Class.forName("com.mysplitter.selector.NoLoadBalanceSelector");
        Class<?> groupClass = Class.forName("com.mysplitter.MySplitterDataSourceGroup");
        Object selector = selectorClass.getConstructor().newInstance();
        Constructor<?> constructor = groupClass.getConstructor(String.class, String.class, String.class, selectorInterface);
        return constructor.newInstance("database-main:writers", "database-main", "writers", selector);
    }

    private Object createDataSourceWrapper() throws Exception {
        Class<?> nodeConfigClass = Class.forName("com.mysplitter.config.MySplitterDataSourceNodeConfig");
        Class<?> loadBalanceConfigClass = Class.forName("com.mysplitter.config.MySplitterLoadBalanceConfig");
        Class<?> wrapperClass = Class.forName("com.mysplitter.DataSourceWrapper");

        Object nodeConfig = nodeConfigClass.getConstructor().newInstance();
        invoke(nodeConfigClass, nodeConfig, "setWeight",
                new Class<?>[]{Integer.class}, new Object[]{Integer.valueOf(1)});

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("url", "jdbc:mysql://localhost:3306/test");
        invoke(nodeConfigClass, nodeConfig, "setConfiguration",
                new Class<?>[]{Map.class}, new Object[]{configuration});

        Object loadBalanceConfig = loadBalanceConfigClass.getConstructor().newInstance();
        invoke(loadBalanceConfigClass, loadBalanceConfig, "setFailTimeout",
                new Class<?>[]{String.class}, new Object[]{"4s"});

        Constructor<?> constructor = wrapperClass.getConstructor(String.class, String.class,
                nodeConfigClass, loadBalanceConfigClass);
        return constructor.newInstance("writer-node", "database-main", nodeConfig, loadBalanceConfig);
    }

    @SuppressWarnings("unchecked")
    private BlockingConcurrentMap replaceSelectorMapWithBlockingMap(Object healthManager,
                                                                    Object group,
                                                                    Object wrapper) throws Exception {
        Class<?> healthManagerClass = healthManager.getClass();
        Field field = healthManagerClass.getDeclaredField("illNodeVersionsBySelector");
        field.setAccessible(true);
        Map<String, Map<String, Long>> allVersions = (Map<String, Map<String, Long>>) field.get(healthManager);
        String selectorName = (String) invoke(group.getClass(), group, "getSelectorName", new Class<?>[0], new Object[0]);
        String nodeName = (String) invoke(wrapper.getClass(), wrapper, "getNodeName", new Class<?>[0], new Object[0]);
        Map<String, Long> existing = allVersions.get(selectorName);
        BlockingConcurrentMap blockingMap = new BlockingConcurrentMap(nodeName);
        if (existing != null) {
            blockingMap.putAll(existing);
        }
        allVersions.put(selectorName, blockingMap);
        return blockingMap;
    }

    private Object invoke(Class<?> targetClass,
                          Object target,
                          String methodName,
                          Class<?>[] parameterTypes,
                          Object[] args) throws Exception {
        Method method = targetClass.getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    private Object invokeDeclared(Class<?> targetClass,
                                  Object target,
                                  String methodName,
                                  Class<?>[] parameterTypes,
                                  Object[] args) throws Exception {
        Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static final class AlerterHandler implements InvocationHandler {

        private final AtomicInteger alertCount = new AtomicInteger(0);

        private Object createProxy(Class<?> alerterClass) {
            return Proxy.newProxyInstance(alerterClass.getClassLoader(), new Class<?>[]{alerterClass}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("alert".equals(method.getName())) {
                alertCount.incrementAndGet();
            }
            return null;
        }
    }

    private static final class CapturingScheduledExecutor extends ScheduledThreadPoolExecutor {

        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();

        private CapturingScheduledExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            tasks.add(command);
            return new ImmediateScheduledFuture();
        }

        private Runnable pollTask() {
            return tasks.poll();
        }
    }

    private static final class ImmediateScheduledFuture implements ScheduledFuture<Object> {

        @Override
        public long getDelay(TimeUnit unit) {
            return 0L;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    }

    private static final class BlockingConcurrentMap extends ConcurrentHashMap<String, Long> {

        private final String blockedNodeName;

        private final CountDownLatch removeAttempted = new CountDownLatch(1);

        private final CountDownLatch continueRemove = new CountDownLatch(1);

        private final AtomicBoolean shouldBlock = new AtomicBoolean(true);

        private BlockingConcurrentMap(String blockedNodeName) {
            this.blockedNodeName = blockedNodeName;
        }

        @Override
        public Long remove(Object key) {
            awaitBlockIfNeeded(key);
            return super.remove(key);
        }

        @Override
        public boolean remove(Object key, Object value) {
            awaitBlockIfNeeded(key);
            return super.remove(key, value);
        }

        private boolean awaitRemoveAttempt(long timeout, TimeUnit unit) throws InterruptedException {
            return removeAttempted.await(timeout, unit);
        }

        private void releaseRemoveAttempt() {
            continueRemove.countDown();
        }

        private void awaitBlockIfNeeded(Object key) {
            if (!blockedNodeName.equals(key) || !shouldBlock.compareAndSet(true, false)) {
                return;
            }
            removeAttempted.countDown();
            try {
                continueRemove.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to continue node removal.", e);
            }
        }
    }
}
