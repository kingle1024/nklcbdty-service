package com.nklcbdty.api.admin.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class AdminSubscriptionDetailDto {
    private String userId;
    private String username;
    private String email;
    private List<AdminSubscriptionItemDto> items;
}
