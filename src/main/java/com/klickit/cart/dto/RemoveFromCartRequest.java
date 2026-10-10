package com.klickit.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RemoveFromCartRequest {

    @NotBlank(message = "Session ID is required")
    @jakarta.validation.constraints.Size(max = 64, message = "Session ID must not exceed 64 characters")
    private String sessionId;

    @NotNull(message = "Product ID is required")
    private UUID productId;
}
