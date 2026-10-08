package com.klickit.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Customer name must not exceed 100 characters")
    private String customerName;

    @NotBlank(message = "Customer phone is required")
    @Size(max = 20, message = "Customer phone must not exceed 20 characters")
    @Pattern(regexp = "^[+0-9\\-\\s()]{7,20}$", message = "Customer phone must be a valid phone number")
    private String customerPhone;

    @NotBlank(message = "Customer address is required")
    @Size(max = 255, message = "Customer address must not exceed 255 characters")
    private String customerAddress;

    /**
     * Optional legacy deadline parameter accepted for backward compatibility,
     * but ignored by OrderService which calculates the authoritative 15-minute SLA server-side.
     */
    private Instant deadline;

    /**
     * Optional client-supplied email parameter.
     * Ignored by OrderService which derives authoritative ownership strictly from the authenticated principal.
     */
    private String customerEmail;

    public CheckoutRequest(String sessionId, String customerName, String customerPhone, String customerAddress, Instant deadline) {
        this.sessionId = sessionId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerAddress = customerAddress;
        this.deadline = deadline;
    }
}
