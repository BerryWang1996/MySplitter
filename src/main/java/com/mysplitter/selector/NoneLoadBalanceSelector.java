package com.mysplitter.selector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 无负载均衡选择器
 */
public class NoneLoadBalanceSelector<T> extends AbstractLoadBalanceSelector<T> {

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
        return list.get(0);
    }

    @Override
    public synchronized void release(T object) {
        if (object == null) {
            return;
        }
        list.remove(object);
    }

    @Override
    public List<T> listAll() {
        return list;
    }

}