package com.mysplitter.selector;

import com.mysplitter.util.StringUtil;

import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * 随机权重算法负载均衡选择器
 *
 * @Author: wangbor
 * @Date: 2018/5/14 19:28
 */
public class RandomLoadBalanceSelector extends AbstractLoadBalanceSelector {

    private List<String> list = new CopyOnWriteArrayList<String>();

    @Override
    public synchronized void register(String name, int weight) {
        for (String key : list) {
            if (key.contains(name)) {
                release(name);
            }
        }
        for (int i = 0; i < weight; i++) {
            list.add(name);
        }
    }

    @Override
    public synchronized String acquire() {
        return list.size() == 0 ? null : list.get(new Random().nextInt(list.size()));
    }

    @Override
    public synchronized void release(String name) {
        if (StringUtil.isBlank(name)) {
            return;
        }
        for (String key : list) {
            if (name.equals(key)) {
                list.remove(name);
            }
        }
    }

}
