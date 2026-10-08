package com.klickit.user.service;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Passwordless email-OTP sign-in.
 *
 * <ul>
 *   <li>Existing account (any role): signs in as that account, role comes from the database.</li>
 *   <li>Unknown email: a CUSTOMER account is created. ADMIN and DELIVERY_PARTNER accounts are never
 *       created or promoted here; they must already exist.</li>
 * </ul>
 * Codes are stored as an HMAC (not plaintext), expire, allow a limited number of guesses, and are
 * single-use. Every failure to verify returns the same message so nothing about accounts leaks.
 */
@Service
@Slf4j
public class OtpService {

    static final String INVALID_MESSAGE = "Invalid or expired code";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    private final String secret;
    private final long ttlMinutes;
    private final int maxAttempts;
    private final long resendCooldownSeconds;
    private final int maxRequestsPerHour;
    private final boolean devLogCode;

    public OtpService(
            OtpCodeRepository otpCodeRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            @Value("${klickit.otp.secret}") String secret,
            @Value("${klickit.otp.ttl-minutes:5}") long ttlMinutes,
            @Value("${klickit.otp.max-attempts:5}") int maxAttempts,
            @Value("${klickit.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${klickit.otp.max-requests-per-hour:5}") int maxRequestsPerHour,
            @Value("${klickit.otp.dev-log-code:false}") boolean devLogCode) {
        this.otpCodeRepository = otpCodeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.secret = secret;
        this.ttlMinutes = ttlMinutes;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxRequestsPerHour = maxRequestsPerHour;
        this.devLogCode = devLogCode;
    }

    /** Generates a code and emails it. Works the same whether or not the email has an account. */
    public void requestCode(String rawEmail) {
        String email = normalize(rawEmail);
        Instant now = Instant.now();

        Optional<OtpCode> latest = otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(email);
        if (latest.isPresent()
                && latest.get().getCreatedAt() != null
                && latest.get().getCreatedAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            throw new OtpRateLimitException("Please wait a moment before requesting another code");
        }
        if (otpCodeRepository.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1))) >= maxRequestsPerHour) {
            throw new OtpRateLimitException("Too many code requests. Please try again later");
        }

        // Only the newest code is ever valid.
        otpCodeRepository.invalidateActive(email, now);

        String code = generateCode();
        OtpCode saved = otpCodeRepository.save(OtpCode.builder()
                .email(email)
                .codeHash(hash(email, code))
                .expiresAt(now.plus(Duration.ofMinutes(ttlMinutes)))
                .attempts(0)
                .build());

        boolean sent = emailService.sendLoginCode(email, code, ttlMinutes);
        if (!sent) {
            if (devLogCode) {
                log.warn("DEV ONLY: email not sent; OTP for {} is {}", email, code);
            } else {
                otpCodeRepository.delete(saved);
                throw new IllegalStateException("We couldn't send the code right now. Please try again");
            }
        }
    }

    /** Verifies the code and, on success, returns a JWT plus the account's role. */
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public AuthResponse verifyCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        Instant now = Instant.now();

        OtpCode otp = otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new InvalidOtpException(INVALID_MESSAGE));

        if (otp.getExpiresAt().isBefore(now) || otp.getAttempts() >= maxAttempts) {
            otp.setConsumedAt(now);
            throw new InvalidOtpException(INVALID_MESSAGE);
        }

        otp.setAttempts(otp.getAttempts() + 1);

        if (!matches(email, code, otp.getCodeHash())) {
            if (otp.getAttempts() >= maxAttempts) {
                otp.setConsumedAt(now);
            }
            throw new InvalidOtpException(INVALID_MESSAGE);
        }

        otp.setConsumedAt(now);

        User user = findOrCreateCustomer(email);
        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .build();
    }

    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void purgeOldCodes() {
        long removed = otpCodeRepository.deleteByExpiresAtBefore(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.debug("Purged {} expired OTP records", removed);
        }
    }

    private User findOrCreateCustomer(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            String localPart = email.substring(0, email.indexOf('@'));
            User created = User.builder()
                    .name(localPart.isBlank() ? "Customer" : localPart)
                    .email(email)
                    .phone(null)
                    // Unusable random password: this account signs in with email OTP only.
                    .password(passwordEncoder.encode(randomSecret()))
                    .role(Role.CUSTOMER)
                    .build();
            return userRepository.save(created);
        });
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private boolean matches(String email, String code, String expectedHash) {
        byte[] actual = hash(email, code == null ? "" : code).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("OTP hashing unavailable", e);
        }
    }
}
