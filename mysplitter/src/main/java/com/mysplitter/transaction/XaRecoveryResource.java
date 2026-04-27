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
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;

public class XaRecoveryResource implements AutoCloseable {

    private final String resourceId;

    private final XAConnection xaConnection;

    private final XAResource xaResource;

    public XaRecoveryResource(String resourceId, XAConnection xaConnection, XAResource xaResource) {
        if (resourceId == null || resourceId.trim().length() == 0) {
            throw new IllegalArgumentException("MySplitter XA recovery resourceId is empty.");
        }
        if (xaConnection == null) {
            throw new IllegalArgumentException("MySplitter XA recovery connection is null.");
        }
        if (xaResource == null) {
            throw new IllegalArgumentException("MySplitter XA recovery resource is null.");
        }
        this.resourceId = resourceId;
        this.xaConnection = xaConnection;
        this.xaResource = xaResource;
    }

    public String getResourceId() {
        return resourceId;
    }

    public XAResource getXaResource() {
        return xaResource;
    }

    @Override
    public void close() throws SQLException {
        xaConnection.close();
    }
}
