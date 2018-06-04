package com.mysplitter.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.mysplitter.MySplitterDataSource;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MySplitterDataSourceTest {

    @Test
    public void testInit() throws Exception {
        MySplitterDataSource router = new MySplitterDataSource();
        router.init();
        MySplitterRootConfig mySplitterConfig = router.getMySplitterConfig();
        String string = JSON.toJSONString(mySplitterConfig, SerializerFeature.DisableCircularReferenceDetect);
        System.out.println(string);
        Connection connection = router.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement("select 1");
        ResultSet resultSet = preparedStatement.executeQuery();
        if (resultSet.next()) {
            String string1 = resultSet.getString(1);
            System.out.println(string1);
        }
        router.close();
    }

}
