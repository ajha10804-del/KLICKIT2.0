package com.klickit.order.dto;

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
public class AssignDeliveryRequest {

    @NotNull(message = "Delivery partner ID is required")
    private UUID deliveryPartnerId;
}
