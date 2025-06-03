package com.nklcbdty.api.user.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class UserResponseDto {
    private String username;
    private String email;
}
