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

import java.io.Serializable;

public class TransactionLogEntry implements Serializable {

    private String globalTransactionId;

    private String branchId;

    private String resourceId;

    private TransactionStatus status;

    public String getGlobalTransactionId() {
        return globalTransactionId;
    }

    public void setGlobalTransactionId(String globalTransactionId) {
        this.globalTransactionId = globalTransactionId;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "TransactionLogEntry{" +
                "globalTransactionId='" + globalTransactionId + '\'' +
                ", branchId='" + branchId + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", status=" + status +
                '}';
    }
}
