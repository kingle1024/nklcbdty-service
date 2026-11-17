package com.nklcbdty.api.admin.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/** 관리자 접속 로그 한 줄 */
@Getter
@Builder
public class AdminVisitorRowDto {
    private Long id;
    private String path;
    /** 접속 IP. 팀원이면 별칭(예: 지용_사무실) */
    private String ip;
    /** DOMESTIC / FOREIGN / INTERNAL / UNKNOWN */
    private String origin;
    private boolean domestic;
    private String countryName;
    private String regionName;
    private String cityName;
    private String referer;
    private String acceptLanguage;
    private LocalDateTime insertDts;
}
