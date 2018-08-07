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

package com.mysplitter;

import java.sql.Savepoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事务保存点代理
 */
public class MySplitterSavepointProxy implements Savepoint {

    private static AtomicInteger atomicInteger = new AtomicInteger(1);

    private static final String DEFAULT_SAVEPOINT_NAME_PREFIX = "MySplitterSavepointProxy";

    private Integer id;

    private String name;

    private Map<Integer, Savepoint> realSavepointMap = new ConcurrentHashMap<>();

    public MySplitterSavepointProxy() {
        atomicInteger.compareAndSet(Integer.MAX_VALUE, 0);
        this.id = atomicInteger.incrementAndGet();
        this.name = DEFAULT_SAVEPOINT_NAME_PREFIX + "-" + atomicInteger.get();
    }

    public MySplitterSavepointProxy(String name) {
        atomicInteger.compareAndSet(Integer.MAX_VALUE, 0);
        this.id = atomicInteger.incrementAndGet();
        this.name = name;
    }

    @Override
    public int getSavepointId() {
        return this.id;
    }

    @Override
    public String getSavepointName() {
        return this.name;
    }

    public void putSavepoint(int hashcode, Savepoint savepoint) {
        realSavepointMap.put(hashcode, savepoint);
    }

    public void clearSavepoint() {
        realSavepointMap.clear();
    }

    public Savepoint getSavepoint(Integer hashcode) {
        return this.realSavepointMap.get(hashcode);
    }

}