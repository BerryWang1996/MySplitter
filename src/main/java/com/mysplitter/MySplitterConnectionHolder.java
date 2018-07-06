package com.mysplitter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 数据源连接保持器（用于在操作数据源时获取当前正在操作的真正的数据源连接）
 */
public class MySplitterConnectionHolder {

    private List<Connection> connections = new CopyOnWriteArrayList<Connection>();

    /**
     * 设置当前操作真正的数据连接
     */
    public void setCurrent(Connection connection) {
        connections.add(connection);
    }

    /**
     * 获取当前操作真正的数据连接
     */
    public Connection getCurrent() {
        return connections.get(connections.size() - 1);
    }

    /**
     * 获取所有的数据连接
     */
    public synchronized List<Connection> listAll() {
        return connections;
    }

    /**
     * 关闭所有的数据源链接
     */
    public synchronized void closeAll() {
        for (Connection connection : connections) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 关闭并删除所有的数据源链接
     */
    public synchronized void clearAll() {
        closeAll();
        connections.clear();
    }

}
