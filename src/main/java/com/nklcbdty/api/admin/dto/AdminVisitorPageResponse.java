package com.nklcbdty.api.admin.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminVisitorPageResponse {
    private List<AdminVisitorRowDto> rows;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
    /** 같은 기간·검색어에서 출처별 건수(국내만·팀원 제외 조건과 무관) */
    private Map<String, Long> originCounts;
}
