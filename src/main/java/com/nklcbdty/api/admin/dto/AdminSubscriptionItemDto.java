package com.nklcbdty.api.admin.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class AdminSubscriptionItemDto {
    private Long id;
    private String itemType;
    private String itemValue;
    private LocalDateTime insertDts;
    private LocalDateTime updateDts;
}
