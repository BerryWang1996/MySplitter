package com.mysplitter.util;

import com.mysplitter.config.MySplitterConfig;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigurationUtilSafeYamlTest {

    @Test
    public void shouldLoadConfigurationThroughSafeYamlMapping() throws Exception {
        MySplitterRootConfig rootConfig = load(
                "mysplitter:\n" +
                        "  enablePasswordEncryption: false\n" +
                        "  readAndWriteParser: com.example.Parser\n" +
                        "  filters:\n" +
                        "    - com.example.Filter\n" +
                        "  common:\n" +
                        "    dataSourceClass: com.zaxxer.hikari.HikariDataSource\n" +
                        "    loadBalance:\n" +
                        "      read:\n" +
                        "        enabled: true\n" +
                        "        strategy: random\n" +
                        "        failTimeout: 10s\n" +
                        "  databases:\n" +
                        "    database-a:\n" +
                        "      readers:\n" +
                        "        reader-1:\n" +
                        "          weight: 2\n" +
                        "          configuration:\n" +
                        "            jdbcUrl: jdbc:h2:mem:test\n" +
                        "            username: sa\n" +
                        "            maximumPoolSize: 1\n");

        MySplitterConfig config = rootConfig.getMysplitter();
        MySplitterDataBaseConfig databaseConfig = config.getDatabases().get("database-a");
        MySplitterDataSourceNodeConfig readerConfig = databaseConfig.getReaders().get("reader-1");

        assertNotNull(config);
        assertEquals("com.example.Parser", config.getReadAndWriteParser());
        assertEquals("com.example.Filter", config.getFilters().get(0));
        assertEquals("com.zaxxer.hikari.HikariDataSource", config.getCommon().getDataSourceClass());
        assertTrue(config.getCommon().getLoadBalance().get("read").isEnabled());
        assertEquals("random", config.getCommon().getLoadBalance().get("read").getStrategy());
        assertEquals("10s", config.getCommon().getLoadBalance().get("read").getFailTimeout());
        assertEquals(Integer.valueOf(2), readerConfig.getWeight());
        assertEquals("jdbc:h2:mem:test", readerConfig.getConfiguration().get("jdbcUrl"));
        assertEquals(Integer.valueOf(1), readerConfig.getConfiguration().get("maximumPoolSize"));
    }

    @Test
    public void shouldRejectDuplicateYamlKeys() throws Exception {
        try {
            load(
                    "mysplitter:\n" +
                            "  common:\n" +
                            "    dataSourceClass: first\n" +
                            "    dataSourceClass: second\n");
            fail("Expected duplicate YAML key to be rejected.");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("duplicate") || String.valueOf(e.getCause()).contains("duplicate"));
        }
    }

    @Test
    public void shouldRejectUnsafeYamlTypeTags() throws Exception {
        try {
            load(
                    "mysplitter: !!javax.script.ScriptEngineManager\n" +
                            "  - !!java.net.URLClassLoader\n" +
                            "    - [!!java.net.URL [\"http://127.0.0.1/\"]]\n");
            fail("Expected unsafe YAML type tag to be rejected.");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    private MySplitterRootConfig load(String yaml) throws Exception {
        return ConfigurationUtil.getMySplitterConfig(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                "inline-test.yml");
    }
}
