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

    /**
     * 개인 일정이라 공개되면 남의 캘린더가 그대로 보인다. 경로가 비슷해서
     * 공개인 {@code /api/calendar/**} 에 딸려 들어가기 쉬우므로 못박아 둔다.
     */
    @Test
    @DisplayName("나의 채용 캘린더는 공개 목록에 없다 — 공개 채용 캘린더와 다른 경로다")
    void myCalendarStaysPrivate() {
        assertThat(isPublic("/api/my-calendar/entries")).isFalse();
        assertThat(isPublic("/api/my-calendar/entries/12")).isFalse();
        assertThat(isPublic("/api/my-calendar/company-name")).isFalse();
    }

    /**
     * 트러블슈팅 기록에는 사내·고객사 시스템 이름과 장애 내용, 로그 원문이 그대로 들어 있다.
     * 공개 목록에 들어가면 그게 전부 인증 없이 열린다. 컨트롤러에 따로 권한 검사를 두지 않고
     * {@code AuthFilter} 의 {@code /api/admin/} 분기(role=ADMIN 요구)에 기대고 있으므로,
     * 이 경로가 공개로 새지 않는다는 것이 그 전제다.
     */
    @Test
    @DisplayName("트러블슈팅 기록은 공개 목록에 없다 — 관리자 토큰이 있어야 한다")
    void troubleshootingStaysPrivate() {
        assertThat(isPublic("/api/admin/troubleshooting")).isFalse();
        assertThat(isPublic("/api/admin/troubleshooting/dart-tracker-list-json-page-shift-21000")).isFalse();
    }
}
