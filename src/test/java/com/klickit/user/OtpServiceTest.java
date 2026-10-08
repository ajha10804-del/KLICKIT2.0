package com.klickit.user;

import com.klickit.common.exception.InvalidOtpException;
import com.klickit.common.exception.OtpRateLimitException;
import com.klickit.config.JwtService;
import com.klickit.notification.service.EmailService;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.entity.OtpCode;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.OtpCodeRepository;
import com.klickit.user.repository.UserRepository;
import com.klickit.user.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private EmailService emailService;

    private OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(otpCodeRepository, userRepository, passwordEncoder, jwtService, emailService,
                "test-secret", 5, 3, 60, 5, false);
        when(otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(otpCodeRepository.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emailService.sendLoginCode(anyString(), anyString(), anyLong())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");
    }

    /** Runs requestCode and returns the stored record plus the plaintext code that was emailed. */
    private record Issued(OtpCode record, String code) {}

    private Issued issue(String email) {
        service.requestCode(email);
        ArgumentCaptor<OtpCode> saved = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(saved.capture());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendLoginCode(eq(email.trim().toLowerCase()), code.capture(), anyLong());
        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email.trim().toLowerCase()))
                .thenReturn(Optional.of(saved.getValue()));
        return new Issued(saved.getValue(), code.getValue());
    }

    private static User user(String email, Role role) {
        User u = User.builder().name("Existing").email(email).phone("9000000001").password("x").role(role).build();
        u.setId(UUID.randomUUID());
        return u;
    }

    @Test
    @DisplayName("Stored record holds a hash, never the plaintext code")
    void storesHashNotCode() {
        Issued issued = issue("a@test.com");
        assertTrue(issued.code().matches("\\d{6}"));
        assertTrue(!issued.record().getCodeHash().contains(issued.code()));
        assertEquals(0, issued.record().getAttempts());
    }

    @Test
    @DisplayName("Existing ADMIN signs in and the role comes from the account")
    void existingAdminGetsAdminRole() {
        when(userRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(user("admin@test.com", Role.ADMIN)));
        Issued issued = issue("Admin@Test.com ");

        AuthResponse res = service.verifyCode("admin@test.com", issued.code());

        assertEquals(Role.ADMIN, res.getRole());
        assertEquals("jwt-token", res.getToken());
        assertNotNull(issued.record().getConsumedAt());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Existing DELIVERY_PARTNER keeps their role")
    void existingDriverGetsDeliveryRole() {
        when(userRepository.findByEmailIgnoreCase("driver@test.com")).thenReturn(Optional.of(user("driver@test.com", Role.DELIVERY_PARTNER)));
        Issued issued = issue("driver@test.com");

        assertEquals(Role.DELIVERY_PARTNER, service.verifyCode("driver@test.com", issued.code()).getRole());
    }

    @Test
    @DisplayName("Unknown email becomes a CUSTOMER with no phone")
    void unknownEmailBecomesCustomer() {
        when(userRepository.findByEmailIgnoreCase("new@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        Issued issued = issue("new@test.com");

        AuthResponse res = service.verifyCode("new@test.com", issued.code());

        assertEquals(Role.CUSTOMER, res.getRole());
        assertEquals("new@test.com", res.getEmail());
        assertEquals("new", res.getName());
        assertNull(res.getPhone());
    }

    @Test
    @DisplayName("Wrong code is rejected, counts an attempt, and does not sign in")
    void wrongCodeRejected() {
        Issued issued = issue("a@test.com");
        String wrong = issued.code().equals("000000") ? "111111" : "000000";

        assertThrows(InvalidOtpException.class, () -> service.verifyCode("a@test.com", wrong));

        assertEquals(1, issued.record().getAttempts());
        assertNull(issued.record().getConsumedAt());
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    @DisplayName("Code is locked after max attempts, even if the right code is then supplied")
    void lockedAfterMaxAttempts() {
        when(userRepository.findByEmailIgnoreCase("a@test.com")).thenReturn(Optional.of(user("a@test.com", Role.CUSTOMER)));
        Issued issued = issue("a@test.com");
        String wrong = issued.code().equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 3; i++) {
            assertThrows(InvalidOtpException.class, () -> service.verifyCode("a@test.com", wrong));
        }
        assertNotNull(issued.record().getConsumedAt());

        // The real code no longer works: the record is consumed, so the repository finds nothing active.
        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc("a@test.com")).thenReturn(Optional.empty());
        assertThrows(InvalidOtpException.class, () -> service.verifyCode("a@test.com", issued.code()));
    }

    @Test
    @DisplayName("Expired code is rejected")
    void expiredCodeRejected() {
        Issued issued = issue("a@test.com");
        issued.record().setExpiresAt(Instant.now().minusSeconds(1));

        assertThrows(InvalidOtpException.class, () -> service.verifyCode("a@test.com", issued.code()));
        assertNotNull(issued.record().getConsumedAt());
    }

    @Test
    @DisplayName("Requesting again inside the cooldown is rate limited and sends nothing")
    void cooldownEnforced() {
        OtpCode recent = OtpCode.builder().email("a@test.com").codeHash("h").expiresAt(Instant.now().plusSeconds(300)).build();
        recent.setCreatedAt(Instant.now().minusSeconds(10));
        when(otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc("a@test.com")).thenReturn(Optional.of(recent));

        assertThrows(OtpRateLimitException.class, () -> service.requestCode("a@test.com"));
        verify(emailService, never()).sendLoginCode(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Hourly request cap is enforced")
    void hourlyCapEnforced() {
        when(otpCodeRepository.countByEmailAndCreatedAtAfter(eq("a@test.com"), any())).thenReturn(5L);

        assertThrows(OtpRateLimitException.class, () -> service.requestCode("a@test.com"));
    }

    @Test
    @DisplayName("If the email cannot be sent the code is discarded and an error is raised")
    void emailFailureDiscardsCode() {
        when(emailService.sendLoginCode(anyString(), anyString(), anyLong())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.requestCode("a@test.com"));
        verify(otpCodeRepository).delete(any(OtpCode.class));
    }
}
