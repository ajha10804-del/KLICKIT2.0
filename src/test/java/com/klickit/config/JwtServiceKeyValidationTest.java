package com.klickit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService Startup Key Validation Tests")
class JwtServiceKeyValidationTest {

    @Test
    @DisplayName("Valid 256-bit Base64 key succeeds startup validation")
    void validKeySucceeds() {
        JwtService service = new JwtService();
        String validKey = Base64.getEncoder().encodeToString(
                "this-is-a-32-byte-secret-key-12345678".getBytes()
        );
        ReflectionTestUtils.setField(service, "secretKey", validKey);
        ReflectionTestUtils.setField(service, "expirationMs", 3600000L);

        assertThatCode(service::validateKeyOnStartup)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Short key (< 32 bytes) throws IllegalStateException on startup")
    void shortKeyThrows() {
        JwtService service = new JwtService();
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());
        ReflectionTestUtils.setField(service, "secretKey", shortKey);

        assertThatThrownBy(service::validateKeyOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 256 bits (32 bytes)");
    }

    @Test
    @DisplayName("Malformed Base64 key throws IllegalStateException on startup")
    void malformedBase64Throws() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", "not-valid-base-64!!!");

        assertThatThrownBy(service::validateKeyOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid Base64");
    }

    @Test
    @DisplayName("Blank key throws IllegalStateException on startup")
    void blankKeyThrows() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", "   ");

        assertThatThrownBy(service::validateKeyOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be configured");
    }

    @Test
    @DisplayName("generateToken includes role claim and extractRole extracts it")
    void generateToken_includesRoleClaim() {
        JwtService service = new JwtService();
        String validKey = Base64.getEncoder().encodeToString(
                "this-is-a-32-byte-secret-key-12345678".getBytes()
        );
        ReflectionTestUtils.setField(service, "secretKey", validKey);
        ReflectionTestUtils.setField(service, "expirationMs", 3600000L);

        com.klickit.user.entity.User user = com.klickit.user.entity.User.builder()
                .email("admin@klickit.com")
                .role(com.klickit.user.entity.Role.ADMIN)
                .build();

        String token = service.generateToken(user);
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(service.extractUsername(token)).isEqualTo("admin@klickit.com");
        org.assertj.core.api.Assertions.assertThat(service.extractRole(token)).isEqualTo("ADMIN");
    }
}
