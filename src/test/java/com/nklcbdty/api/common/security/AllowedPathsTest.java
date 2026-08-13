package com.nklcbdty.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 로그인 없이 볼 수 있어야 하는 화면의 API 가 공개 경로로 남아 있는지 고정한다.
 *
 * <p>{@code AuthFilter} 가 실제로 쓰는 것과 같은 방식(와일드카드를 정규식으로 바꿔 매칭)으로 검사한다.
 * 여기서 빠지면 비로그인 사용자에게 401 이 떨어진다.</p>
 */
class AllowedPathsTest {

    private boolean isPublic(String requestUri) {
        return Arrays.stream(AllowedPaths.getAllowedPaths())
                     .anyMatch(path -> requestUri.matches(path.replace("**", ".*")));
    }

    @Test
    @DisplayName("채용 캘린더는 로그인 없이 볼 수 있다")
    void calendarIsPublic() {
        assertThat(isPublic("/api/calendar/deadlines")).isTrue();
    }

    @Test
    @DisplayName("공고 목록 · 카테고리 · 회사 목록도 로그인 없이 볼 수 있다")
    void publicListScreensStayPublic() {
        assertThat(isPublic("/api/list")).isTrue();
        assertThat(isPublic("/api/category/list")).isTrue();
        assertThat(isPublic("/api/company/list")).isTrue();
    }

    @Test
    @DisplayName("공지사항·자유게시판 목록·상세는 로그인 없이 볼 수 있다")
    void boardIsPublic() {
        assertThat(isPublic("/api/boards/free/posts")).isTrue();
        assertThat(isPublic("/api/boards/free/posts/12")).isTrue();
        assertThat(isPublic("/api/boards/notice/posts")).isTrue();
    }

    @Test
    @DisplayName("공지사항 작성용 관리자 API 는 공개 목록에 없다")
    void adminBoardStaysPrivate() {
        assertThat(isPublic("/api/admin/boards/notice/posts")).isFalse();
    }

    @Test
    @DisplayName("마이페이지처럼 로그인이 필요한 경로는 공개 목록에 없다")
    void privatePathsStayPrivate() {
        assertThat(isPublic("/mypage")).isFalse();
        assertThat(isPublic("/api/admin/subscriptions")).isFalse();
    }
}
