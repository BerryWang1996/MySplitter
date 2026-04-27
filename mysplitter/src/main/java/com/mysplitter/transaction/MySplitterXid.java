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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.transaction.xa.Xid;

public final class MySplitterXid implements Xid {

    private static final int FORMAT_ID = 0x4D53504C;

    private final byte[] globalTransactionId;

    private final byte[] branchQualifier;

    public MySplitterXid(String globalTransactionId, String branchQualifier) {
        this.globalTransactionId = toXidPart(globalTransactionId, "globalTransactionId", Xid.MAXGTRIDSIZE);
        this.branchQualifier = toXidPart(branchQualifier, "branchQualifier", Xid.MAXBQUALSIZE);
    }

    @Override
    public int getFormatId() {
        return FORMAT_ID;
    }

    @Override
    public byte[] getGlobalTransactionId() {
        return Arrays.copyOf(globalTransactionId, globalTransactionId.length);
    }

    @Override
    public byte[] getBranchQualifier() {
        return Arrays.copyOf(branchQualifier, branchQualifier.length);
    }

    private static byte[] toXidPart(String value, String name, int maxSize) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("MySplitter XA " + name + " is empty.");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxSize) {
            throw new IllegalArgumentException("MySplitter XA " + name + " exceeds " + maxSize +
                    " bytes.");
        }
        return bytes;
    }

    @Override
    public String toString() {
        return "MySplitterXid{" +
                "formatId=" + FORMAT_ID +
                ", globalTransactionId='" + new String(globalTransactionId, StandardCharsets.UTF_8) + '\'' +
                ", branchQualifier='" + new String(branchQualifier, StandardCharsets.UTF_8) + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Xid)) {
            return false;
        }
        Xid other = (Xid) obj;
        return FORMAT_ID == other.getFormatId() &&
                Arrays.equals(globalTransactionId, other.getGlobalTransactionId()) &&
                Arrays.equals(branchQualifier, other.getBranchQualifier());
    }

    @Override
    public int hashCode() {
        int result = FORMAT_ID;
        result = 31 * result + Arrays.hashCode(globalTransactionId);
        result = 31 * result + Arrays.hashCode(branchQualifier);
        return result;
    }
}
