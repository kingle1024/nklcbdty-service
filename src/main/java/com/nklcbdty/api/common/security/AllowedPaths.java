package com.nklcbdty.api.common.security;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum AllowedPaths {
    LOGIN("/login"),
    KAKAO_LOGIN("/api/kakaoLogin"),
    OAUTH2("/oauth2/**"),
    DETAIL("/detail/**"),
    LIST("/list"),
    EMAIL("/email/send")
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
