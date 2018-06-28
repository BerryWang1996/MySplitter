package com.mysplitter.selector;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 随机权重算法负载均衡选择器
 */
public class RandomLoadBalanceSelector<T> extends AbstractLoadBalanceSelector<T> {

    private List<T> list = new CopyOnWriteArrayList<T>();

    @Override
    public synchronized void register(T object, int weight) {
        for (T key : list) {
            if (key == object) {
                release(object);
            }
        }
        for (int i = 0; i < weight; i++) {
            list.add(object);
        }
    }

    @Override
    public synchronized T acquire() {
        return list.size() == 0 ? null : list.get(new Random().nextInt(list.size()));
    }

    @Override
    public synchronized void release(T object) {
        if (object == null) {
            return;
        }
        for (T objectInList : list) {
            if (objectInList == object) {
                list.remove(objectInList);
            }
        }
    }

    @Override
    public List<T> listAll() {
        return list;
    }

}
