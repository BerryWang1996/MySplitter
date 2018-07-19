/*
 * Copyright 2018 BerryWang1996
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mysplitter.util;

/**
 * 字符串工具类
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
