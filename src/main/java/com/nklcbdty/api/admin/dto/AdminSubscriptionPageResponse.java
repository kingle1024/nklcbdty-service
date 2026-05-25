package com.nklcbdty.api.admin.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class AdminSubscriptionPageResponse {
    private List<AdminSubscriptionRowDto> rows;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
}
