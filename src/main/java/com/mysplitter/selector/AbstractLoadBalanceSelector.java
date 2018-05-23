package com.mysplitter.selector;

import java.util.List;

/**
 * 负载均衡选择器
 *
 * @Author: wangbor
 * @Date: 2018/5/14 18:35
 */
public abstract class AbstractLoadBalanceSelector<T> {

    public abstract void register(T object, int weight);

    public abstract T acquire();

    public abstract void release(T object);

    public abstract List<T> listAll();

}
