package com.mysplitter.util;

import com.mysplitter.config.MySplitterRootConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

/**
 * 配置文件工具类
 *
 * @Author: wangbor
 * @Date: 2018/5/13 18:15
 */
public class ConfigurationUtil {

    private static MySplitterRootConfig mySplitterRootConfig;

    static {
        // 读取配置文件
        InputStream resource = ConfigurationUtil.class.getClassLoader().getResourceAsStream("mysplitter-example.yml");
        // 饿汉式加载配置对象
        Yaml yaml = new Yaml();
        mySplitterRootConfig = yaml.loadAs(resource, MySplitterRootConfig.class);
    }

    private ConfigurationUtil() {
    }

    public static MySplitterRootConfig getMySplitterConfig() {
        return ConfigurationUtil.mySplitterRootConfig;
    }

}
