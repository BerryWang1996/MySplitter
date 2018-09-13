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

package com.mysplitter;

import com.mysplitter.advise.ReadAndWriteParserAdvise;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;

/**
 * 默认的读写解析器
 */
public class DefaultReadAndWriteParser implements ReadAndWriteParserAdvise {

    /**
     * 解析sql是读操作还是写操作
     *
     * @param sql 操作的sql
     * @return readers:是读操作 writers:是写操作
     */
    @Override
    public String parseOperation(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select) {
                return "readers";
            } else {
                return "writers";
            }
        } catch (JSQLParserException ignored) {
        }
        return "writers";
    }

}