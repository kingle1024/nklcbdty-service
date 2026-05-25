package com.nklcbdty.api.admin.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class AdminSubscriptionStatsDto {
    private long totalSubscribers;
    private long totalItems;
    private Map<String, Map<String, Long>> countsByItemType;
}
