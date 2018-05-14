package com.mysplitter.selector;

/**
 * 负载均衡选择器
 *
 * @Author: wangbor
 * @Date: 2018/5/14 18:35
 */
public abstract class AbstractLoadBalanceSelector {

    public abstract void register(String name, int weight);

    public abstract String acquire();

    public abstract void release(String name);

}
