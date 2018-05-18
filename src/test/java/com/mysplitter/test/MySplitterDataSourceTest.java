package com.mysplitter.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.mysplitter.MySplitterDataSource;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.Test;

public class MySplitterDataSourceTest {

    @Test
    public void testInit() throws Exception {
        MySplitterDataSource router = new MySplitterDataSource();
        router.init();
        MySplitterRootConfig mySplitterConfig = router.getMySplitterConfig();
        String string = JSON.toJSONString(mySplitterConfig, SerializerFeature.DisableCircularReferenceDetect);
        System.out.println(string);
    }

}
