package com.klickit.notification;

import com.klickit.notification.client.HttpsTransactionalEmailClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Production Email Client Validation Tests")
class ProductionEmailClientValidationTest {

    @Test
    @DisplayName("Empty MAIL_API_KEY in 'prod' profile throws IllegalStateException")
    void emptyApiKeyInProdThrows() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        HttpsTransactionalEmailClient client = new HttpsTransactionalEmailClient(
                RestClient.builder().build(),
                "",
                "onboarding@resend.dev",
                "https://api.resend.com/emails",
                prodEnv
        );

        assertThatThrownBy(client::validateConfigurationOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_API_KEY must be configured when running under 'prod' profile");
    }

    @Test
    @DisplayName("Configured MAIL_API_KEY in 'prod' profile succeeds startup validation")
    void configuredApiKeyInProdSucceeds() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        HttpsTransactionalEmailClient client = new HttpsTransactionalEmailClient(
                RestClient.builder().build(),
                "re_valid_api_key_12345",
                "orders@example.com",
                "https://api.resend.com/emails",
                prodEnv
        );

        assertThatCode(client::validateConfigurationOnStartup)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Empty MAIL_API_KEY in non-prod profile logs warning and does not throw")
    void emptyApiKeyInDevDoesNotThrow() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");

        HttpsTransactionalEmailClient client = new HttpsTransactionalEmailClient(
                RestClient.builder().build(),
                "",
                "onboarding@resend.dev",
                "https://api.resend.com/emails",
                devEnv
        );

        assertThatCode(client::validateConfigurationOnStartup)
                .doesNotThrowAnyException();
    }
}
