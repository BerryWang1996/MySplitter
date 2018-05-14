package com.mysplitter.test;

import com.mysplitter.MySplitterDataSourceRouter;
import org.junit.Test;

public class MySplitterDataSourceRouterTest {

    @Test
    public void testInit() throws Exception {
        MySplitterDataSourceRouter router = new MySplitterDataSourceRouter();
        router.init();
    }

}
