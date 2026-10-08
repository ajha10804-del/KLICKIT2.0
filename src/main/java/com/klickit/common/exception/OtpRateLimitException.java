package com.klickit.common.exception;

/** Thrown when OTP requests are too frequent. Mapped to HTTP 429. */
public class OtpRateLimitException extends RuntimeException {

    public OtpRateLimitException(String message) {
        super(message);
    }
}
