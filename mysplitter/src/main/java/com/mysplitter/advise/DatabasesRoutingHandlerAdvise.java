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

package com.mysplitter.advise;

/**
 * 多数据库路由处理器接口类
 */
public interface DatabasesRoutingHandlerAdvise {

    /**
     * @param sql sql
     * @return database name (in mysplitter.yml)
     */
    String routerHandler(String sql);

    /**
     * @param sql sql
     * @return new sql
     */
    String rewriteSql(String sql);

}
