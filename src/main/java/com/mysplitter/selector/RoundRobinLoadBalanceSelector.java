package com.mysplitter.selector;

import com.mysplitter.util.StringUtil;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询算法负载均衡选择器
 *
 * @Author: wangbor
 * @Date: 2018/5/14 19:28
 */
public class RoundRobinLoadBalanceSelector extends AbstractLoadBalanceSelector {

    private List<String> list = new CopyOnWriteArrayList<String>();

    private AtomicInteger atomicFetch = new AtomicInteger(0);

    @Override
    public synchronized void register(String name, int weight) {
        if (!list.contains(name)) {
            list.add(name);
        }
    }

    @Override
    public synchronized String acquire() {
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
    public synchronized void release(String name) {
        if (StringUtil.isBlank(name)) {
            return;
        }
        list.remove(name);
    }

}
