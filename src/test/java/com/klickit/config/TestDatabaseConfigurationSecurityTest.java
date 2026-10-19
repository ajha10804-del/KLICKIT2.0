package com.klickit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseConfigurationSecurityTest {

    @Test
    @DisplayName("Test configuration explicitly requires TEST_DB_URL and does not fall back to DB_URL")
    void testDatasourceConfig_requiresTestDbUrlWithoutFallback() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties props = factory.getObject();

        assertThat(props).isNotNull();
        String datasourceUrl = props.getProperty("spring.datasource.url");
        String datasourceUsername = props.getProperty("spring.datasource.username");
        String datasourcePassword = props.getProperty("spring.datasource.password");

        // The property must be the unresolved placeholder ${TEST_DB_URL}, NOT a fallback to localhost or DB_URL
        assertThat(datasourceUrl).isEqualTo("${TEST_DB_URL}");
        assertThat(datasourceUsername).isEqualTo("${TEST_DB_USERNAME}");
        assertThat(datasourcePassword).isEqualTo("${TEST_DB_PASSWORD}");
    }

    @Test
    @DisplayName("When TEST_DB_URL is missing, resolving placeholder fails fast with IllegalArgumentException")
    void missingTestDbUrl_failsFast() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties props = factory.getObject();
        assertThat(props).isNotNull();

        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        // Environment contains DB_URL (e.g. production DB URL in developer's terminal), but NOT TEST_DB_URL
        env.getPropertySources().addFirst(new MapPropertySource("prodShellEnv", Map.of(
                "DB_URL", "postgresql://prod-user:prod-pass@prod-host:5432/prod_db",
                "DB_USERNAME", "prod-user",
                "DB_PASSWORD", "prod-pass"
        )));

        // Attempting to resolve ${TEST_DB_URL} when only DB_URL is present must fail
        assertThatThrownBy(() -> env.resolveRequiredPlaceholders(props.getProperty("spring.datasource.url")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Could not resolve placeholder 'TEST_DB_URL'");
    }

    @Test
    @DisplayName("When TEST_DB_URL is explicitly provided, it resolves cleanly to the dedicated test database")
    void providedTestDbUrl_resolvesCleanly() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties props = factory.getObject();
        assertThat(props).isNotNull();

        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("testEnv", Map.of(
                "TEST_DB_URL", "jdbc:postgresql://localhost:5432/klickit_test",
                "TEST_DB_USERNAME", "klickit_test_user",
                "TEST_DB_PASSWORD", "klickit_test_password"
        )));

        String resolvedUrl = env.resolveRequiredPlaceholders(props.getProperty("spring.datasource.url"));
        String resolvedUser = env.resolveRequiredPlaceholders(props.getProperty("spring.datasource.username"));
        String resolvedPass = env.resolveRequiredPlaceholders(props.getProperty("spring.datasource.password"));

        assertThat(resolvedUrl).isEqualTo("jdbc:postgresql://localhost:5432/klickit_test");
        assertThat(resolvedUser).isEqualTo("klickit_test_user");
        assertThat(resolvedPass).isEqualTo("klickit_test_password");
    }
}
