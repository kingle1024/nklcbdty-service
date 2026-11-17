package com.nklcbdty.api.log.dto;

import java.util.Date;

import lombok.Builder;
import lombok.Getter;

/** 관리자 접속 로그 조회 조건. 비어 있는 값은 조건에서 빠진다. */
@Getter
@Builder
public class VisitorSearchCondition {
    /** true 면 국내(DOMESTIC + INTERNAL)만 */
    private final boolean domesticOnly;
    /** true 면 팀원 접속(INTERNAL)을 뺀다 */
    private final boolean excludeInternal;
    /** 경로·IP 부분 일치 */
    private final String keyword;
    /** 이 시각 이후(포함) */
    private final Date from;
    /** 이 시각 이전(미포함) */
    private final Date to;
}
