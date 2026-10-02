package com.klickit.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the PRODUCTION application.yml directly from the filesystem
 * (src/main/resources/application.yml) so that it is NOT shadowed by
 * src/test/resources/application.yml on the test classpath.
 *
 * A MapPropertySource simulates the environment variables that a
 * deployment host would set.  Assertions verify that every
 * placeholder in the production YAML resolves to the simulated value.
 *
 * No Spring context is created — no database, no beans, no ports.
 */
class ProductionConfigBindingTest {

    private static StandardEnvironment env;

    // Simulated environment variable values
    private static final String SIM_MAIL_API_KEY = "re_test_sim_key_123";
    private static final String SIM_MAIL_FROM    = "test@example.com";
    private static final String SIM_PORT         = "10000";
    private static final String SIM_ADMIN_EMAIL  = "admin@example.com";
    private static final String SIM_JWT_SECRET   = "dGVzdC1kdW1teS1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtMjU2LWJpdHMtbG9uZw==";
    private static final String SIM_CORS_ORIGINS = "https://app.example.com";

    @BeforeAll
    static void loadProductionYaml() throws IOException {
        env = new StandardEnvironment();

        // 1. Simulate env vars — added FIRST so the YAML placeholders can resolve
        Map<String, Object> simEnv = new HashMap<>();
        simEnv.put("MAIL_API_KEY", SIM_MAIL_API_KEY);
        simEnv.put("MAIL_FROM", SIM_MAIL_FROM);
        simEnv.put("PORT", SIM_PORT);
        simEnv.put("ADMIN_EMAIL", SIM_ADMIN_EMAIL);
        simEnv.put("JWT_SECRET", SIM_JWT_SECRET);
        simEnv.put("CORS_ALLOWED_ORIGINS", SIM_CORS_ORIGINS);
        simEnv.put("BOOTSTRAP_ADMIN_NAME", "Simulated Admin");
        simEnv.put("BOOTSTRAP_ADMIN_EMAIL", "sim_admin@example.com");
        simEnv.put("BOOTSTRAP_ADMIN_PHONE", "9999999999");
        simEnv.put("BOOTSTRAP_ADMIN_PASSWORD", "simulated_password");
        // DB_PASSWORD is required by the YAML but irrelevant to the mail/port tests
        simEnv.put("DB_PASSWORD", "unused");

        MutablePropertySources sources = env.getPropertySources();
        sources.addFirst(new MapPropertySource("simulated-env", simEnv));

        // 2. Load the PRODUCTION YAML from the filesystem (not classpath)
        FileSystemResource yamlFile = new FileSystemResource("src/main/resources/application.yml");
        assertThat(yamlFile.exists())
                .as("Production application.yml must exist at src/main/resources/application.yml")
                .isTrue();

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> yamlSources = loader.load("production-yaml", yamlFile);
        for (PropertySource<?> ps : yamlSources) {
            sources.addLast(ps);
        }
    }

    // ── klickit.mail.* ──────────────────────────────────────────────

    @Test
    @DisplayName("klickit.mail.api-key resolves to MAIL_API_KEY env var")
    void mailApiKeyResolvesToEnvVar() {
        assertThat(env.getProperty("klickit.mail.api-key")).isEqualTo(SIM_MAIL_API_KEY);
    }

    @Test
    @DisplayName("klickit.mail.from resolves to MAIL_FROM env var")
    void mailFromResolvesToEnvVar() {
        assertThat(env.getProperty("klickit.mail.from")).isEqualTo(SIM_MAIL_FROM);
    }

    @Test
    @DisplayName("klickit.mail.api-url resolves to default when MAIL_API_URL is unset")
    void mailApiUrlResolvesToDefault() {
        // MAIL_API_URL is NOT in the simulated env, so the YAML default must kick in
        assertThat(env.getProperty("klickit.mail.api-url"))
                .isEqualTo("https://api.resend.com/emails");
    }

    @Test
    @DisplayName("klickit.mail.timezone resolves to default when MAIL_TIMEZONE is unset")
    void mailTimezoneResolvesToDefault() {
        assertThat(env.getProperty("klickit.mail.timezone"))
                .isEqualTo("Asia/Kolkata");
    }

    // ── server.port ─────────────────────────────────────────────────

    @Test
    @DisplayName("server.port resolves to PORT env var")
    void serverPortResolvesToEnvVar() {
        assertThat(env.getProperty("server.port")).isEqualTo(SIM_PORT);
    }

    @Test
    @DisplayName("server.port defaults to 8080 when PORT env var is absent")
    void serverPortDefaultsTo8080() throws IOException {
        // Build a fresh environment WITHOUT PORT in the simulated env
        StandardEnvironment envNoPort = new StandardEnvironment();
        Map<String, Object> simEnvNoPort = new HashMap<>();
        simEnvNoPort.put("ADMIN_EMAIL", SIM_ADMIN_EMAIL);
        simEnvNoPort.put("JWT_SECRET", SIM_JWT_SECRET);
        simEnvNoPort.put("DB_PASSWORD", "unused");

        MutablePropertySources sources = envNoPort.getPropertySources();
        sources.addFirst(new MapPropertySource("simulated-env-no-port", simEnvNoPort));

        FileSystemResource yamlFile = new FileSystemResource("src/main/resources/application.yml");
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> yamlSources = loader.load("production-yaml-no-port", yamlFile);
        for (PropertySource<?> ps : yamlSources) {
            sources.addLast(ps);
        }

        assertThat(envNoPort.getProperty("server.port")).isEqualTo("8080");
    }

    // ── klickit.admin.email ─────────────────────────────────────────

    @Test
    @DisplayName("klickit.admin.email resolves to ADMIN_EMAIL env var")
    void adminEmailResolvesToEnvVar() {
        assertThat(env.getProperty("klickit.admin.email")).isEqualTo(SIM_ADMIN_EMAIL);
    }

    // ── klickit.cors.allowed-origins ────────────────────────────────

    @Test
    @DisplayName("klickit.cors.allowed-origins resolves to CORS_ALLOWED_ORIGINS env var")
    void corsOriginsResolvesToEnvVar() {
        assertThat(env.getProperty("klickit.cors.allowed-origins")).isEqualTo(SIM_CORS_ORIGINS);
    }

    // ── JWT ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("klickit.jwt.secret resolves to JWT_SECRET env var")
    void jwtSecretResolvesToEnvVar() {
        assertThat(env.getProperty("klickit.jwt.secret")).isEqualTo(SIM_JWT_SECRET);
    }

    // ── klickit.bootstrap.admin.* ───────────────────────────────────

    @Test
    @DisplayName("klickit.bootstrap.admin.name resolves to BOOTSTRAP_ADMIN_NAME env var")
    void bootstrapAdminNameResolvesToEnvVar() {
        assertThat(env.getProperty("klickit.bootstrap.admin.name")).isEqualTo("Simulated Admin");
    }

    @Test
    @DisplayName("klickit.bootstrap.admin.email resolves to BOOTSTRAP_ADMIN_EMAIL env var")
    void bootstrapAdminEmailResolvesToEnvVar() {
        assertThat(env.getProperty("klickit.bootstrap.admin.email")).isEqualTo("sim_admin@example.com");
    }

    @Test
    @DisplayName("klickit.bootstrap.admin.phone resolves to BOOTSTRAP_ADMIN_PHONE env var")
    void bootstrapAdminPhoneResolvesToEnvVar() {
        assertThat(env.getProperty("klickit.bootstrap.admin.phone")).isEqualTo("9999999999");
    }

    @Test
    @DisplayName("klickit.bootstrap.admin.password resolves to BOOTSTRAP_ADMIN_PASSWORD env var")
    void bootstrapAdminPasswordResolvesToEnvVar() {
        assertThat(env.getProperty("klickit.bootstrap.admin.password")).isEqualTo("simulated_password");
    }

    @Test
    @DisplayName("klickit.bootstrap.admin.password defaults to empty when BOOTSTRAP_ADMIN_PASSWORD is absent")
    void bootstrapAdminPasswordDefaultsToEmpty() throws IOException {
        StandardEnvironment envNoBootstrap = new StandardEnvironment();
        Map<String, Object> simEnvNoBootstrap = new HashMap<>();
        simEnvNoBootstrap.put("ADMIN_EMAIL", SIM_ADMIN_EMAIL);
        simEnvNoBootstrap.put("JWT_SECRET", SIM_JWT_SECRET);
        simEnvNoBootstrap.put("DB_PASSWORD", "unused");

        MutablePropertySources sources = envNoBootstrap.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("simulated-env-no-bootstrap", simEnvNoBootstrap));

        FileSystemResource yamlFile = new FileSystemResource("src/main/resources/application.yml");
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> yamlSources = loader.load("production-yaml-no-bootstrap", yamlFile);
        for (PropertySource<?> ps : yamlSources) {
            sources.addLast(ps);
        }

        assertThat(envNoBootstrap.getProperty("klickit.bootstrap.admin.password")).isEmpty();
    }
}
