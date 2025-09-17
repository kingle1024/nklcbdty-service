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
    COUNT_BY_DATE("/api/statistics/count-by-date"),
    TEST("/api/test"),
    SEARCH("/api/job/**"),
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
