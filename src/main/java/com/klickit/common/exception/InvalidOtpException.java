package com.klickit.common.exception;

/** Thrown when an OTP is wrong, expired, already used, or locked out. Mapped to HTTP 401. */
public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException(String message) {
        super(message);
    }
}
