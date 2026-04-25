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

public final class XaResourceDescriptor implements Serializable {

    private final String resourceId;

    private final String dataSourceClassName;

    private final String xaDataSourceClassName;

    private final boolean xaCapable;

    private final String unavailableReason;

    private XaResourceDescriptor(String resourceId,
                                 String dataSourceClassName,
                                 String xaDataSourceClassName,
                                 boolean xaCapable,
                                 String unavailableReason) {
        this.resourceId = resourceId;
        this.dataSourceClassName = dataSourceClassName;
        this.xaDataSourceClassName = xaDataSourceClassName;
        this.xaCapable = xaCapable;
        this.unavailableReason = unavailableReason;
    }

    public static XaResourceDescriptor capable(String resourceId,
                                               String dataSourceClassName,
                                               String xaDataSourceClassName) {
        return new XaResourceDescriptor(resourceId, dataSourceClassName, xaDataSourceClassName, true, null);
    }

    public static XaResourceDescriptor unavailable(String resourceId,
                                                   String dataSourceClassName,
                                                   String unavailableReason) {
        return new XaResourceDescriptor(resourceId, dataSourceClassName, null, false, unavailableReason);
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDataSourceClassName() {
        return dataSourceClassName;
    }

    public String getXaDataSourceClassName() {
        return xaDataSourceClassName;
    }

    public boolean isXaCapable() {
        return xaCapable;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    @Override
    public String toString() {
        return "XaResourceDescriptor{" +
                "resourceId='" + resourceId + '\'' +
                ", dataSourceClassName='" + dataSourceClassName + '\'' +
                ", xaDataSourceClassName='" + xaDataSourceClassName + '\'' +
                ", xaCapable=" + xaCapable +
                ", unavailableReason='" + unavailableReason + '\'' +
                '}';
    }
}
