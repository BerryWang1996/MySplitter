/*
 * Copyright 2018 BerryWang1996
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mysplitter.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询算法负载均衡选择器
 */
public class RoundRobinLoadBalanceSelector<T> implements LoadBalanceSelector<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinLoadBalanceSelector.class);

    private List<T> list = new CopyOnWriteArrayList<T>();

    private AtomicInteger atomicFetch = new AtomicInteger(0);

    @Override
    public synchronized void register(T object, int weight) {
        LOGGER.debug("Registers {} weight {}.", object, weight);
        if (!list.contains(object)) {
            list.add(object);
        }
    }

    @Override
    public synchronized T acquire() {
        LOGGER.debug("Acquire somethings.");
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
        LOGGER.debug("Release {}.", object);
        if (object == null) {
            return;
        }
        list.remove(object);
    }

    @Override
    public List<T> listAll() {
        return new ArrayList<>(list);
    }

}
