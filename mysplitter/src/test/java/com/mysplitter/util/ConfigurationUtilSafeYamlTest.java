package com.mysplitter.util;

import com.mysplitter.config.MySplitterConfig;
import com.mysplitter.config.MySplitterDataBaseConfig;
import com.mysplitter.config.MySplitterDataSourceNodeConfig;
import com.mysplitter.config.MySplitterRootConfig;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigurationUtilSafeYamlTest {

    private static final String EXPERIMENTAL_XA_PROPERTY = "mysplitter.experimental.xa.enabled";

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

    @Test
    public void shouldDefaultTransactionModeToLocalWhenMissing() throws Exception {
        MySplitterRootConfig rootConfig = loadChecked(singleNodeYaml("", ""));

        assertNotNull(rootConfig.getMysplitter().getTransaction());
        assertEquals("local", rootConfig.getMysplitter().getTransaction().getMode());
    }

    @Test
    public void shouldLoadLocalTransactionConfiguration() throws Exception {
        MySplitterRootConfig rootConfig = loadChecked(singleNodeYaml(
                "  transaction:\n" +
                        "    mode: LOCAL\n" +
                        "    coordinator:\n" +
                        "      type: embedded\n" +
                        "      logStore: jdbc\n" +
                        "      logFile: ./target/mysplitter-xa.log\n" +
                        "    recovery:\n" +
                        "      enabled: true\n" +
                        "      interval: 10s\n",
                ""));

        assertEquals("local", rootConfig.getMysplitter().getTransaction().getMode());
        assertEquals("embedded", rootConfig.getMysplitter().getTransaction().getCoordinator().getType());
        assertEquals("jdbc", rootConfig.getMysplitter().getTransaction().getCoordinator().getLogStore());
        assertEquals("./target/mysplitter-xa.log",
                rootConfig.getMysplitter().getTransaction().getCoordinator().getLogFile());
        assertTrue(rootConfig.getMysplitter().getTransaction().getRecovery().isEnabled());
        assertEquals("10s", rootConfig.getMysplitter().getTransaction().getRecovery().getInterval());
    }

    @Test
    public void shouldRejectUnknownTransactionMode() throws Exception {
        try {
            loadChecked(singleNodeYaml(
                    "  transaction:\n" +
                            "    mode: best-effort\n",
                    ""));
            fail("Expected unknown transaction mode to be rejected.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("transaction.mode not support"));
        }
    }

    @Test
    public void shouldGateXaTransactionModeByDefault() throws Exception {
        try {
            loadChecked(singleNodeYaml(
                    "  transaction:\n" +
                            "    mode: xa\n",
                    ""));
            fail("Expected XA mode to stay gated by default.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("xa is implemented internally but still"));
        }
    }

    @Test
    public void shouldAllowXaTransactionModeWhenExperimentalGateIsEnabled() throws Exception {
        System.setProperty(EXPERIMENTAL_XA_PROPERTY, "true");
        try {
            MySplitterRootConfig rootConfig = loadChecked(singleNodeYaml(
                    "  transaction:\n" +
                            "    mode: XA\n",
                    ""));

            assertEquals("xa", rootConfig.getMysplitter().getTransaction().getMode());
        } finally {
            System.clearProperty(EXPERIMENTAL_XA_PROPERTY);
        }
    }

    @Test
    public void shouldKeepPlainYamlPasswordWhenConfigured() throws Exception {
        MySplitterRootConfig rootConfig = loadChecked(singleNodeYaml(
                "  passwordSource: plain\n",
                "            password: local-secret\n"));

        assertEquals("local-secret", nodeConfiguration(rootConfig).get("password"));
    }

    @Test
    public void shouldResolvePasswordFromSystemPropertyPlaceholder() throws Exception {
        System.setProperty("mysplitter.test.password", "external-secret");
        try {
            MySplitterRootConfig rootConfig = loadChecked(singleNodeYaml(
                    "  passwordSource: environment\n",
                    "            password: ${mysplitter.test.password}\n"));

            assertEquals("external-secret", nodeConfiguration(rootConfig).get("password"));
        } finally {
            System.clearProperty("mysplitter.test.password");
        }
    }

    @Test
    public void shouldResolvePasswordFromPasswordEnvKey() throws Exception {
        System.setProperty("mysplitter.test.passwordEnv", "env-key-secret");
        try {
            MySplitterRootConfig rootConfig = loadChecked(singleNodeYaml(
                    "  passwordSource: environment\n",
                    "            passwordEnv: mysplitter.test.passwordEnv\n"));

            assertEquals("env-key-secret", nodeConfiguration(rootConfig).get("password"));
        } finally {
            System.clearProperty("mysplitter.test.passwordEnv");
        }
    }

    @Test
    public void shouldRejectPlainPasswordWhenEnvironmentModeIsConfigured() throws Exception {
        try {
            loadChecked(singleNodeYaml(
                    "  passwordSource: environment\n",
                    "            password: local-secret\n"));
            fail("Expected environment passwordSource to reject plain password values.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("passwordSource environment"));
        }
    }

    @Test
    public void shouldRejectLegacyRsaPasswordWithoutExplicitPublicKey() throws Exception {
        try {
            loadChecked(singleNodeYaml(
                    "  enablePasswordEncryption: true\n",
                    "            password: encrypted-secret\n"));
            fail("Expected legacy RSA mode to require an explicit public key.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("publicKey explicitly"));
        }
    }

    @Test
    public void shouldRejectSecurityUtilDefaultPrivateKeyUsage() throws Exception {
        try {
            SecurityUtil.encrypt("secret");
            fail("Expected RSA encryption helper to require an explicit private key.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("private key"));
        }
    }

    private MySplitterRootConfig load(String yaml) throws Exception {
        return ConfigurationUtil.getMySplitterConfig(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                "inline-test.yml");
    }

    private MySplitterRootConfig loadChecked(String yaml) throws Exception {
        MySplitterRootConfig rootConfig = load(yaml);
        ConfigurationUtil.checkMySplitterConfig(rootConfig);
        return rootConfig;
    }

    private String singleNodeYaml(String mySplitterOptions, String configurationOptions) {
        return "mysplitter:\n" +
                mySplitterOptions +
                "  common:\n" +
                "    dataSourceClass: com.zaxxer.hikari.HikariDataSource\n" +
                "  databases:\n" +
                "    database-a:\n" +
                "      integrates:\n" +
                "        node-1:\n" +
                "          configuration:\n" +
                "            jdbcUrl: jdbc:h2:mem:test\n" +
                "            username: sa\n" +
                configurationOptions;
    }

    private Map<String, Object> nodeConfiguration(MySplitterRootConfig rootConfig) {
        return rootConfig.getMysplitter()
                .getDatabases()
                .get("database-a")
                .getIntegrates()
                .get("node-1")
                .getConfiguration();
    }
}
