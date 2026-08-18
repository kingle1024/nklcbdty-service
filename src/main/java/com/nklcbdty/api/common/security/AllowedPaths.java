package com.nklcbdty.api.common.security;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum AllowedPaths {
    LOGIN("/login"),
    KAKAO_LOGIN("/api/kakaoLogin"),
    // 자체 회원가입/로그인. 인증 전 단계이므로 공개.
    AUTH_SIGNUP("/api/auth/signup"),
    AUTH_LOGIN("/api/auth/login"),
    AUTH_EMAIL_EXISTS("/api/auth/email-exists"),
    OAUTH2("/oauth2/**"),
    DETAIL("/detail/**"),
    LOG("/api/log/**"),
    LIST("/api/list"),
    LIST_ALL("/api/list/**"),
    CATEGORY_ALL("/api/category/**"),
    // 회사 목록 + 채용 페이지 주소. 로그인 없이 보는 목록 화면에서 쓰므로 공개.
    COMPANY_ALL("/api/company/**"),
    // 채용 캘린더(마감일 기준). 목록 화면과 같이 로그인 없이 보는 화면이라 공개.
    CALENDAR_ALL("/api/calendar/**"),
    EMAIL("/api/email/**"),
    CRALWER("/api/crawler"),
    JOB_DELETE_REQUEST("/api/job-delete-requests"),
    JOB_DELETE_REQUEST_ALL("/api/job-delete-requests/**"),
    // 게시판(공지사항/자유게시판). 비로그인 열람과 익명 작성을 허용하므로 필터를 통과시키고,
    // 로그인 여부/권한은 BoardController 와 BoardService 에서 판별한다.
    // (공지사항 작성·수정·삭제는 ADMIN 을 요구하는 /api/admin/boards/** 로 분리)
    BOARD("/api/boards"),
    BOARD_ALL("/api/boards/**"),
    COUNT_BY_DATE("/api/statistics/count-by-date"),
    TEST("/api/test"),
    SEARCH("/api/job/**"),
    // 공고 의미 검색(/api/jobs/search)과 이력서 PDF 매칭(/api/jobs/match). 인증 없이 공개.
    // ('/api/job/**' 는 정규식상 's' 가 붙은 '/api/jobs/...' 를 매치하지 못해 별도 등록)
    JOBS("/api/jobs/**"),
    // 관리자 로그인만 공개. 그 외 /api/admin/** 은 AuthFilter 에서 ADMIN 역할 토큰을 요구한다.
    ADMIN_LOGIN("/api/admin/login"),
    // 지금 떠 있는 빌드 확인용(빌드/기동 시각). 배포가 실제로 반영됐는지 보려면 인증 없이 열려 있어야 한다.
    VERSION("/api/version"),
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
