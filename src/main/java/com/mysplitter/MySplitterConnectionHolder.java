package com.mysplitter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MySplitterConnectionHolder {

    private List<Connection> connections = new CopyOnWriteArrayList<Connection>();

    public void setCurrent(Connection connection) {
        connections.add(connection);
    }

    public Connection getCurrent() {
        return connections.get(connections.size() - 1);
    }

    public synchronized List<Connection> listAll() {
        return connections;
    }

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

    public synchronized void clearAll() {
        closeAll();
        connections.clear();
    }

}
