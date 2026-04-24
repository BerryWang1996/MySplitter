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

package com.mysplitter.spring.boot.autoconfigure;

import com.mysplitter.MySplitterDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.util.Objects;

@AutoConfiguration
@ConditionalOnClass(MySplitterDataSource.class)
@EnableConfigurationProperties(MySplitterConfigureProperties.class)
public class MySplitterAutoConfigure {

    private final MySplitterConfigureProperties properties;

    private final MySplitterConfigurationLoader configurationLoader;

    public MySplitterAutoConfigure(MySplitterConfigureProperties properties, ResourceLoader resourceLoader) {
        this(properties, new MySplitterConfigurationLoader(resourceLoader));
    }

    MySplitterAutoConfigure(MySplitterConfigureProperties properties,
                            MySplitterConfigurationLoader configurationLoader) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.configurationLoader = Objects.requireNonNull(configurationLoader, "configurationLoader");
    }

    @Bean(initMethod = "init", destroyMethod = "close")
    @ConditionalOnMissingBean
    public MySplitterDataSource mySplitterDataSource() throws IOException {
        return configurationLoader.loadDataSource(properties.getConfigurationFile());
    }
}
