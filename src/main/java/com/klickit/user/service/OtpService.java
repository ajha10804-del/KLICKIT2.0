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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpCodeRepository otpCodeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${klickit.otp.enabled:true}")
    private boolean enabled;

    @Value("${klickit.otp.secret:}")
    private String secret;

    @Value("${klickit.otp.ttl-minutes:5}")
    private long ttlMinutes;

    @Value("${klickit.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${klickit.otp.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${klickit.otp.max-requests-per-hour:5}")
    private int maxRequestsPerHour;

    @Value("${klickit.otp.dev-log-code:false}")
    private boolean devLogCode;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Object[] emailLocks = new Object[128];
    {
        for (int i = 0; i < emailLocks.length; i++) {
            emailLocks[i] = new Object();
        }
    }

    private Object getEmailLock(String email) {
        return emailLocks[(email.hashCode() & 0x7fffffff) % emailLocks.length];
    }

    @PostConstruct
    public void validateConfiguration() {
        if (enabled && (secret == null || secret.isBlank())) {
            throw new IllegalStateException("OTP_SECRET must be configured when OTP authentication is enabled");
        }
    }

    @Transactional
    public void requestOtp(String email) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }

        synchronized (getEmailLock(normalizedEmail)) {
            try {
                otpCodeRepository.acquireEmailLock(normalizedEmail);
            } catch (Exception e) {
                log.debug("Advisory lock skipped or unsupported: {}", e.getMessage());
            }

            // 1. Resend cooldown check
            Optional<OtpCode> lastOtpOpt = otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(normalizedEmail);
            if (lastOtpOpt.isPresent()) {
                Instant cooldownCutoff = Instant.now().minusSeconds(resendCooldownSeconds);
                if (lastOtpOpt.get().getCreatedAt().isAfter(cooldownCutoff)) {
                    throw new OtpRateLimitException("Please wait before requesting another sign-in code");
                }
            }

            // 2. Hourly request limit check
            Instant hourlyCutoff = Instant.now().minus(1, ChronoUnit.HOURS);
            long requestCountLastHour = otpCodeRepository.countByEmailAndCreatedAtAfter(normalizedEmail, hourlyCutoff);
            if (requestCountLastHour >= maxRequestsPerHour) {
                throw new OtpRateLimitException("Too many sign-in code requests. Please try again later");
            }

            // 3. Invalidate any existing active OTPs for this email
            otpCodeRepository.invalidateActive(normalizedEmail, Instant.now());

            // 4. Generate code and digest
            String code = generateNumericCode();
            String codeHash = computeHmacSha256(normalizedEmail, code);
            Instant now = Instant.now();

            OtpCode otp = OtpCode.builder()
                    .email(normalizedEmail)
                    .codeHash(codeHash)
                    .expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES))
                    .attempts(0)
                    .build();
            otp.setCreatedAt(now);
            otp.setUpdatedAt(now);

            otpCodeRepository.save(otp);

            // 5. Send email
            boolean sent = emailService.sendLoginCode(normalizedEmail, code, ttlMinutes);
            if (!sent) {
                if (devLogCode) {
                    log.info("[DEV ONLY] Login OTP code for {}: {} (email delivery skipped: local dev mode)", normalizedEmail, code);
                } else {
                    log.error("Failed to send OTP login email to {}", normalizedEmail);
                    otpCodeRepository.delete(otp);
                    return;
                }
            } else if (devLogCode) {
                log.info("[DEV ONLY] Login OTP code for {}: {}", normalizedEmail, code);
            }
        }
    }

    @Transactional(noRollbackFor = InvalidOtpException.class)
    public AuthResponse verifyOtp(String email, String code) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";
        if (normalizedEmail.isBlank() || code == null || code.isBlank()) {
            throw new InvalidOtpException("Invalid or expired code");
        }

        // Row lock on active unconsumed code prevents concurrent verification race conditions
        Optional<OtpCode> otpOpt = otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(normalizedEmail);
        if (otpOpt.isEmpty()) {
            throw new InvalidOtpException("Invalid or expired code");
        }

        OtpCode otp = otpOpt.get();
        Instant now = Instant.now();

        // Verify expiration
        if (otp.getExpiresAt().isBefore(now)) {
            throw new InvalidOtpException("Invalid or expired code");
        }

        // Verify attempts threshold
        if (otp.getAttempts() >= maxAttempts) {
            throw new InvalidOtpException("Invalid or expired code");
        }

        // Constant-time digest comparison
        String expectedHash = computeHmacSha256(normalizedEmail, code);
        if (!matchesDigest(expectedHash, otp.getCodeHash())) {
            int newAttempts = otp.getAttempts() + 1;
            otp.setAttempts(newAttempts);
            if (newAttempts >= maxAttempts) {
                otp.setConsumedAt(now);
            }
            otp.setUpdatedAt(now);
            otpCodeRepository.save(otp);
            throw new InvalidOtpException("Invalid or expired code");
        }

        // Consume OTP
        otp.setConsumedAt(now);
        otp.setUpdatedAt(now);
        otpCodeRepository.save(otp);

        // Resolve user or auto-provision CUSTOMER
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> provisionCustomer(normalizedEmail));

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

    @Scheduled(cron = "${klickit.otp.cleanup-cron:0 15 * * * ?}")
    @Transactional
    public void purgeExpiredOtpCodes() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        long deleted = otpCodeRepository.deleteByExpiresAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} expired OTP codes", deleted);
        }
    }

    private User provisionCustomer(String email) {
        String name = (email != null && email.contains("@"))
                ? email.substring(0, email.indexOf('@'))
                : "Customer";
        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.CUSTOMER)
                .build();
        return userRepository.save(user);
    }

    private String generateNumericCode() {
        int code = 100_000 + secureRandom.nextInt(900_000);
        return String.valueOf(code);
    }

    private String computeHmacSha256(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            String payload = email.toLowerCase().trim() + ":" + code.trim();
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate OTP HMAC digest", e);
        }
    }

    private boolean matchesDigest(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                actualHex.getBytes(StandardCharsets.UTF_8));
    }
}
