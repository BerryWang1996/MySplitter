package com.mysplitter.test;

import com.mysplitter.advise.DataSourceFilterAdvise;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestRouteRecordingFilter implements DataSourceFilterAdvise {

    private static final List<String> ROUTES = new CopyOnWriteArrayList<String>();

    @Override
    public void doFilter(String databaseName, String nodeName, String sql) {
        ROUTES.add(databaseName + "|" + nodeName + "|" + sql);
    }

    public static void reset() {
        ROUTES.clear();
    }

    public static List<String> snapshot() {
        return new ArrayList<String>(ROUTES);
    }
}
