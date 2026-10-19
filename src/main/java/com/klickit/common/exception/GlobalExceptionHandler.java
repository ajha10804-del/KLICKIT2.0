package com.klickit.common.exception;

import com.klickit.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final Pattern DB_CREDENTIAL_PATTERN = Pattern.compile("(?i)(postgres(?:ql)?://)([^:@\\s]+):([^@\\s]+)@");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)bearer\\s+[a-z0-9\\-_.~+/]+=*");
    private static final Pattern JWT_PATTERN = Pattern.compile("ey[A-Za-z0-9\\-_]+\\.ey[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(re_[a-zA-Z0-9_]{10,}|key-[a-zA-Z0-9_]{10,})");
    private static final Pattern PASSWORD_PARAM_PATTERN = Pattern.compile("(?i)(password|secret|token|apiKey|api_key)\\s*[=:]\\s*['\"]?[^'\"\\s,;&]+['\"]?");
    private static final Pattern OTP_PATTERN = Pattern.compile("(?i)(otp|code)\\s*[=:]\\s*\\d{6}");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(RuntimeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUsernameNotFound(UsernameNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(org.springframework.security.authentication.DisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Account is disabled. Please contact support."));
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOtp(InvalidOtpException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleOtpRateLimit(OtpRateLimitException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(org.springframework.web.method.annotation.HandlerMethodValidationException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation error: " + ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        String errorId = java.util.UUID.randomUUID().toString();
        String exType = ex != null ? ex.getClass().getName() : "UnknownException";
        String rootCauseType = extractRootCauseClass(ex);
        String location = extractSafeLocation(ex);
        String method = request != null ? request.getMethod() : "N/A";
        String uri = request != null ? request.getRequestURI() : "N/A";

        // Safe-by-design logging: The original Throwable and dynamic exception messages are NOT passed to the logger.
        // This guarantees that raw database connection strings, passwords, JWTs, Bearer headers, OTPs, or PII
        // in exception messages or nested causes can never be emitted into log streams.
        log.error("Unhandled server exception: [errorId={}] [type={}] [rootCause={}] [method={}] [uri={}] [location={}]",
                errorId, exType, rootCauseType, method, uri, location);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }

    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        return handleGeneral(ex, null);
    }

    private String extractRootCauseClass(Throwable ex) {
        if (ex == null) {
            return "None";
        }
        Throwable root = ex;
        java.util.Set<Throwable> visited = new java.util.HashSet<>();
        while (root.getCause() != null && root.getCause() != root && visited.add(root)) {
            root = root.getCause();
        }
        return root.getClass().getName();
    }

    private String extractSafeLocation(Throwable ex) {
        if (ex == null || ex.getStackTrace() == null || ex.getStackTrace().length == 0) {
            return "unknown";
        }
        for (StackTraceElement element : ex.getStackTrace()) {
            if (element != null && element.getClassName() != null && element.getClassName().startsWith("com.klickit")) {
                return element.getClassName() + "." + element.getMethodName() + ":" + element.getLineNumber();
            }
        }
        StackTraceElement top = ex.getStackTrace()[0];
        return top != null ? top.getClassName() + "." + top.getMethodName() + ":" + top.getLineNumber() : "unknown";
    }

    public static String sanitizeForLogging(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String sanitized = message;
        sanitized = DB_CREDENTIAL_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED_USER]:[REDACTED_PASSWORD]@");
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("[JWT_REDACTED]");
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("[API_KEY_REDACTED]");
        sanitized = PASSWORD_PARAM_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
        sanitized = OTP_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
        return sanitized;
    }
}
