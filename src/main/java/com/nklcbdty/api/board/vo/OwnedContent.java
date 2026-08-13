package com.nklcbdty.api.board.vo;

/**
 * 작성자 소유권 판정이 필요한 게시판 콘텐츠(게시글/댓글) 공통 규약.
 * - authorId 가 있으면 로그인 사용자 글 → 같은 userId 만 수정/삭제
 * - passwordHash 가 있으면 익명 글 → 비밀번호 일치 시 수정/삭제
 * - 둘 다 없으면 관리자가 쓴 글 → 관리자만 수정/삭제
 */
public interface OwnedContent {

    String getAuthorId();

    String getPasswordHash();
}
