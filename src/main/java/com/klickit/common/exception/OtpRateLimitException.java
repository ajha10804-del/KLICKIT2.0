package com.klickit.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class OtpRateLimitException extends RuntimeException {

    public OtpRateLimitException(String message) {
        super(message);
    }
}
