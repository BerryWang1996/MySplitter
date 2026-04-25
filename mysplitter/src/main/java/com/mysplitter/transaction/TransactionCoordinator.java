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

package com.mysplitter.transaction;

import java.sql.SQLException;

public interface TransactionCoordinator {

    String begin() throws SQLException;

    void enlist(BranchTransaction branchTransaction) throws SQLException;

    void commit(String globalTransactionId) throws SQLException;

    void rollback(String globalTransactionId) throws SQLException;

    void recover() throws SQLException;
}
