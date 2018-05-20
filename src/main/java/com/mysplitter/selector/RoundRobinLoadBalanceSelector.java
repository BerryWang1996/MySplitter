package com.mysplitter.selector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询算法负载均衡选择器
 *
 * @Author: wangbor
 * @Date: 2018/5/14 19:28
 */
public class RoundRobinLoadBalanceSelector<T> extends AbstractLoadBalanceSelector<T> {

    private List<T> list = new CopyOnWriteArrayList<T>();

    private AtomicInteger atomicFetch = new AtomicInteger(0);

    @Override
    public synchronized void register(T object, int weight) {
        if (!list.contains(object)) {
            list.add(object);
        }
    }

    @Override
    public synchronized T acquire() {
        if (list.size() == 0) {
            return null;
        }
        int index = atomicFetch.getAndIncrement();
        if (index >= list.size()) {
            atomicFetch.set(1);
            index = 0;
        }
        return list.get(index);
    }

    @Override
    public synchronized void release(T object) {
        if (object == null) {
            return;
        }
        list.remove(object);
    }

}
