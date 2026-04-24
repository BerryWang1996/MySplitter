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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 闅忔満鏉冮噸绠楁硶璐熻浇鍧囪　閫夋嫨鍣?
 */
public class RandomLoadBalanceSelector<T> implements LoadBalanceSelector<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RandomLoadBalanceSelector.class);

    private final Map<T, Integer> weightMap = new LinkedHashMap<T, Integer>();

    private final Random random = new Random();

    @Override
    public synchronized void register(T object, int weight) {
        LOGGER.debug("Registers {} weight {}.", object, weight);
        if (object == null) {
            return;
        }
        weightMap.put(object, weight < 1 ? 1 : weight);
    }

    @Override
    public synchronized T acquire() {
        LOGGER.debug("Acquire somethings.");
        return acquire(listAll());
    }

    @Override
    public synchronized T acquire(List<T> candidates) {
        LOGGER.debug("Acquire somethings from candidates.");
        if (candidates == null || candidates.size() == 0) {
            return null;
        }
        int totalWeight = 0;
        for (T candidate : candidates) {
            totalWeight += getWeight(candidate);
        }
        if (totalWeight <= 0) {
            return candidates.get(0);
        }
        int current = random.nextInt(totalWeight);
        for (T candidate : candidates) {
            current -= getWeight(candidate);
            if (current < 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    @Override
    public synchronized void release(T object) {
        LOGGER.debug("Release {}.", object);
        if (object == null) {
            return;
        }
        weightMap.remove(object);
    }

    @Override
    public synchronized List<T> listAll() {
        return new ArrayList<T>(weightMap.keySet());
    }

    private int getWeight(T object) {
        Integer weight = weightMap.get(object);
        return weight == null || weight.intValue() < 1 ? 1 : weight.intValue();
    }

}
