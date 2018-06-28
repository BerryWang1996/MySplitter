package com.mysplitter;

import java.sql.Savepoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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