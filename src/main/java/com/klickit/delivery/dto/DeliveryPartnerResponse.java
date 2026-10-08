package com.klickit.delivery.dto;

import com.klickit.delivery.entity.DeliveryPartner;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class DeliveryPartnerResponse {

    private final UUID id;
    private final String name;
    private final String phone;
    private final UUID userId;

    public static DeliveryPartnerResponse from(DeliveryPartner partner) {
        return DeliveryPartnerResponse.builder()
                .id(partner.getId())
                .name(partner.getName())
                .phone(partner.getPhone())
                .userId(partner.getUserId())
                .build();
    }
}
