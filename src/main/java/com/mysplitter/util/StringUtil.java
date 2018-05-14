package com.mysplitter.util;

/**
 * 字符串工具类
 *
 * @Author: 王伯瑞
 * @Date: 2018/5/14 10:06
 */
public class StringUtil {

    private StringUtil() {
    }

    public static boolean isBlank(String string) {
        return string == null || "".equals(string.trim());
    }

    public static boolean isNotBlank(String string) {
        return !isBlank(string);
    }

    public static boolean isAnyBlank(String... string) {
        for (String s : string) {
            if (isBlank(s)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllBlank(String... string) {
        for (String s : string) {
            if (isNotBlank(s)) {
                return false;
            }
        }
        return true;
    }

}
