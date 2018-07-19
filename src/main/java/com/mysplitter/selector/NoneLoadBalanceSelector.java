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