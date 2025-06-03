package com.nklcbdty.api.user.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class UserInterestResponseDto {
    private String itemType;
    private String itemValue;
}
