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
import com.mysplitter.config.MySplitterRootConfig;
import com.mysplitter.util.ConfigurationUtil;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

class MySplitterConfigurationLoader {

    private final ResourceLoader resourceLoader;

    MySplitterConfigurationLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    MySplitterDataSource loadDataSource(String configurationLocation) throws IOException {
        return new MySplitterDataSource(loadConfiguration(configurationLocation));
    }

    MySplitterRootConfig loadConfiguration(String configurationLocation) throws IOException {
        Resource resource = resourceLoader.getResource(configurationLocation);
        String description = getDescription(resource, configurationLocation);
        if (!resource.exists()) {
            throw new FileNotFoundException("MySplitter configuration resource " + description + " not found!");
        }
        try {
            return ConfigurationUtil.getMySplitterConfig(resource.getInputStream(), description);
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to load MySplitter configuration from " + description, e);
        }
    }

    private String getDescription(Resource resource, String configurationLocation) {
        String description = resource.getDescription();
        if (StringUtils.hasText(description)) {
            return description;
        }
        return configurationLocation;
    }
}
