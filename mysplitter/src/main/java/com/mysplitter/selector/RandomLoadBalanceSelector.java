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
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 随机权重算法负载均衡选择器
 */
public class RandomLoadBalanceSelector<T> extends AbstractLoadBalanceSelector<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RandomLoadBalanceSelector.class);

    private List<T> list = new CopyOnWriteArrayList<T>();

    @Override
    public synchronized void register(T object, int weight) {
        LOGGER.debug("Registers {} weight {}.", object, weight);
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
        LOGGER.debug("Acquire somethings.");
        return list.size() == 0 ? null : list.get(new Random().nextInt(list.size()));
    }

    @Override
    public synchronized void release(T object) {
        LOGGER.debug("Release {}.", object);
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
        return new ArrayList<>(list);
    }

}
