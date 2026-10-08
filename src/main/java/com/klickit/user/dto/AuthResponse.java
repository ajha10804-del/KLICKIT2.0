package com.klickit.user.dto;

import com.klickit.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {

    private final String token;
    private final UUID userId;
    private final String name;
    private final String email;
    private final String phone;
    private final Role role;
}
