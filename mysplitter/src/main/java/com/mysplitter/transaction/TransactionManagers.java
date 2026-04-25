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

import com.mysplitter.config.MySplitterTransactionConfig;
import com.mysplitter.util.StringUtil;

import java.util.Locale;

public final class TransactionManagers {

    private TransactionManagers() {
    }

    public static GlobalTransactionManager create(MySplitterTransactionConfig transactionConfig) {
        String mode = transactionConfig == null ? null : transactionConfig.getMode();
        if (StringUtil.isBlank(mode)) {
            return new LocalTransactionManager();
        }
        String normalizedMode = mode.trim().toLowerCase(Locale.ENGLISH);
        if (MySplitterTransactionConfig.MODE_LOCAL.equals(normalizedMode)) {
            return new LocalTransactionManager();
        }
        if (MySplitterTransactionConfig.MODE_XA.equals(normalizedMode)) {
            return new UnsupportedDistributedTransactionManager(normalizedMode);
        }
        throw new IllegalArgumentException("MySplitter transaction.mode not support " + mode +
                ". Only supported one of [local, xa].");
    }
}
