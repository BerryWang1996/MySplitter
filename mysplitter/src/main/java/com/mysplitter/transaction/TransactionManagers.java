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
import com.mysplitter.config.MySplitterTransactionCoordinatorConfig;
import com.mysplitter.util.StringUtil;

import java.io.File;
import java.sql.SQLException;
import java.util.Locale;

public final class TransactionManagers {

    private static final String COORDINATOR_EMBEDDED = "embedded";

    private static final String LOG_STORE_MEMORY = "memory";

    private static final String LOG_STORE_FILE = "file";

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

    public static GlobalTransactionManager create(MySplitterTransactionConfig transactionConfig,
                                                  XaResourceRegistry xaResourceRegistry) throws SQLException {
        String mode = transactionConfig == null ? null : transactionConfig.getMode();
        if (StringUtil.isBlank(mode)) {
            return new LocalTransactionManager();
        }
        String normalizedMode = mode.trim().toLowerCase(Locale.ENGLISH);
        if (MySplitterTransactionConfig.MODE_LOCAL.equals(normalizedMode)) {
            return new LocalTransactionManager();
        }
        if (MySplitterTransactionConfig.MODE_XA.equals(normalizedMode)) {
            if (xaResourceRegistry == null) {
                throw new IllegalArgumentException("MySplitter XA resource registry is null.");
            }
            TransactionLogStore transactionLogStore = createTransactionLogStore(transactionConfig);
            XaRecoveryExecutor recoveryExecutor = new XaRecoveryExecutor(transactionLogStore, xaResourceRegistry);
            return new XaTransactionManager(new EmbeddedTransactionCoordinator(transactionLogStore, recoveryExecutor));
        }
        throw new IllegalArgumentException("MySplitter transaction.mode not support " + mode +
                ". Only supported one of [local, xa].");
    }

    private static TransactionLogStore createTransactionLogStore(MySplitterTransactionConfig transactionConfig)
            throws SQLException {
        MySplitterTransactionCoordinatorConfig coordinatorConfig = transactionConfig.getCoordinator();
        String coordinatorType = coordinatorConfig == null ? null : coordinatorConfig.getType();
        if (StringUtil.isBlank(coordinatorType)) {
            coordinatorType = COORDINATOR_EMBEDDED;
        }
        coordinatorType = coordinatorType.trim().toLowerCase(Locale.ENGLISH);
        if (!COORDINATOR_EMBEDDED.equals(coordinatorType)) {
            throw new IllegalArgumentException("MySplitter transaction.coordinator.type not support " +
                    coordinatorType + ". Only embedded is supported in v1.1.0.");
        }

        String logStore = coordinatorConfig == null ? null : coordinatorConfig.getLogStore();
        if (StringUtil.isBlank(logStore)) {
            logStore = LOG_STORE_MEMORY;
        }
        logStore = logStore.trim().toLowerCase(Locale.ENGLISH);
        if (LOG_STORE_MEMORY.equals(logStore)) {
            return new InMemoryTransactionLogStore();
        }
        if (LOG_STORE_FILE.equals(logStore)) {
            String logFile = coordinatorConfig.getLogFile();
            if (StringUtil.isBlank(logFile)) {
                throw new IllegalArgumentException("MySplitter transaction.coordinator.logFile is required when " +
                        "transaction.coordinator.logStore is file.");
            }
            return new FileTransactionLogStore(new File(logFile));
        }
        throw new IllegalArgumentException("MySplitter transaction.coordinator.logStore not support " + logStore +
                ". Only supported one of [memory, file].");
    }
}
