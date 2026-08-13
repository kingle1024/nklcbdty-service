package com.nklcbdty.api.board.vo;

import lombok.Getter;

/** 게시판 종류. URL 경로(notice/free)와 DB 저장값(NOTICE/FREE)을 함께 담당한다. */
@Getter
public enum BoardType {
    /** 공지사항. 읽기는 공개, 쓰기/수정/삭제는 관리자만. */
    NOTICE("공지사항", true),
    /** 자유게시판. 로그인 사용자 또는 익명(닉네임+비밀번호) 모두 작성 가능. */
    FREE("자유게시판", false),
    ;

    private final String label;
    /** 글 작성/수정/삭제에 관리자 권한이 필요한지 */
    private final boolean adminOnlyWrite;

    BoardType(String label, boolean adminOnlyWrite) {
        this.label = label;
        this.adminOnlyWrite = adminOnlyWrite;
    }

    /** 경로 변수(notice, free, NOTICE, FREE ...) 를 BoardType 으로 변환. */
    public static BoardType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("게시판 종류가 필요합니다. (notice / free)");
        }
        try {
            return BoardType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 게시판입니다: " + value + " (notice / free)");
        }
    }
}
