package com.mysplitter.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.mysplitter.MySplitterDataSource;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MySplitterDataSourceTest {

    @Test
    public void testInit() throws Exception {
        MySplitterDataSource router = new MySplitterDataSource();
        router.init();
        MySplitterRootConfig mySplitterConfig = router.getMySplitterConfig();
        String string = JSON.toJSONString(mySplitterConfig, SerializerFeature.DisableCircularReferenceDetect);
        System.out.println(string);
        router.close();
    }

    @Test
    public void testSelect() throws Exception {
        MySplitterDataSource router = new MySplitterDataSource();
        router.init();
        MySplitterRootConfig mySplitterConfig = router.getMySplitterConfig();
        String string = JSON.toJSONString(mySplitterConfig, SerializerFeature.DisableCircularReferenceDetect);
        System.out.println(string);
        Connection connection = router.getConnection();

        PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT * FROM dept WHERE id=?");
        preparedStatement2.setLong(1, 1L);
        ResultSet resultSet2 = preparedStatement2.executeQuery();
        if (resultSet2.next()) {
            String id = resultSet2.getString("id");
            String name = resultSet2.getString("name");
            System.out.println("=====TABLE dept RESULT=====:" + id + "-" + name);
        }

        PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM user WHERE id=?");
        preparedStatement.setLong(1, 1L);
        ResultSet resultSet = preparedStatement.executeQuery();
        if (resultSet.next()) {
            String id = resultSet.getString("id");
            String username = resultSet.getString("name");
            Integer age = resultSet.getInt("age");
            System.out.println("=====TABLE user RESULT=====:" + id + "-" + username + "-" + age);
        }
        router.close();
    }

    @Test
    public void testInsert() throws Exception {
        MySplitterDataSource router = new MySplitterDataSource();
        router.init();
        MySplitterRootConfig mySplitterConfig = router.getMySplitterConfig();
        String string = JSON.toJSONString(mySplitterConfig, SerializerFeature.DisableCircularReferenceDetect);
        System.out.println(string);
        Connection connection = router.getConnection();

        PreparedStatement preparedStatement =
                connection.prepareStatement("INSERT INTO user(username,password,age) VALUES(?,?,?)");
        preparedStatement.setString(1, "wbrrr123123123");
        preparedStatement.setString(2, "wbrrr123123123");
        preparedStatement.setInt(3, 12);
        preparedStatement.executeUpdate();

        PreparedStatement preparedStatement2 =
                connection.prepareStatement("INSERT INTO dept(name) VALUES(?)");
        preparedStatement2.setString(1, "ops-team");
        preparedStatement2.executeUpdate();
        router.close();
    }

    @Test
    public void testTranscational() throws SQLException {
        MySplitterDataSource router = null;
        Connection connection = null;
        try {
            router = new MySplitterDataSource();
            router.init();
            MySplitterRootConfig mySplitterConfig = router.getMySplitterConfig();
            String string = JSON.toJSONString(mySplitterConfig, SerializerFeature.DisableCircularReferenceDetect);
            System.out.println(string);
            connection = router.getConnection();
            connection.setAutoCommit(false);

            PreparedStatement preparedStatement =
                    connection.prepareStatement("DELETE FROM user where id = ?");
            preparedStatement.setLong(1, 5);
            preparedStatement.executeUpdate();

            preparedStatement =
                    connection.prepareStatement("DELETE FROM dept where id = ?");
            preparedStatement.setLong(1, 3);
            preparedStatement.executeUpdate();
            connection.commit();
        } catch (Exception e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
            }
            throw new AssertionError("Transactional test failed.", e);
        } finally {
            if (router != null) {
                router.close();
            }
        }
    }
}
