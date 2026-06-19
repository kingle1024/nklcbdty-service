package com.nklcbdty.api.common.security;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum AllowedPaths {
    LOGIN("/login"),
    KAKAO_LOGIN("/api/kakaoLogin"),
    OAUTH2("/oauth2/**"),
    DETAIL("/detail/**"),
    LOG("/api/log/**"),
    LIST("/api/list"),
    LIST_ALL("/api/list/**"),
    CATEGORY_ALL("/api/category/**"),
    EMAIL("/api/email/**"),
    CRALWER("/api/crawler"),
    JOB_DELETE_REQUEST("/api/job-delete-requests"),
    JOB_DELETE_REQUEST_ALL("/api/job-delete-requests/**"),
    COUNT_BY_DATE("/api/statistics/count-by-date"),
    TEST("/api/test"),
    SEARCH("/api/job/**"),
    // 공고 의미 검색(/api/jobs/search)과 이력서 PDF 매칭(/api/jobs/match). 인증 없이 공개.
    // ('/api/job/**' 는 정규식상 's' 가 붙은 '/api/jobs/...' 를 매치하지 못해 별도 등록)
    JOBS("/api/jobs/**"),
    // 관리자 로그인만 공개. 그 외 /api/admin/** 은 AuthFilter 에서 ADMIN 역할 토큰을 요구한다.
    ADMIN_LOGIN("/api/admin/login"),
    ;

    private final String path;

    AllowedPaths(String path) {
        this.path = path;
    }

    public static String[] getAllowedPaths() {
        return Arrays.stream(AllowedPaths.values())
                     .map(AllowedPaths::getPath)
                     .toArray(String[]::new);
    }
}
