package com.nklcbdty.api.board.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.nklcbdty.api.board.service.BoardSystemWriter.PostRef;
import com.nklcbdty.api.board.vo.BoardType;

/**
 * BoardSystemWriter 의 SQL 을 실제 DB(H2)에 실행해 본다.
 *
 * <p>이 클래스는 JPA 대신 직접 쓴 INSERT 를 쓰므로, 컬럼 개수나 순서가 어긋나도 기동 전에는
 * 아무도 모른다. 운영에서는 "샘플 글이 안 올라온다"로만 보인다(로그를 봐야 이유를 안다).
 * 그래서 여기서 실제로 넣어 보고 다시 읽는다.</p>
 */
class BoardSystemWriterTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private BoardSystemWriter writer;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();
        jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.execute("""
            CREATE TABLE board_post (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              board_type VARCHAR(20) NOT NULL,
              title VARCHAR(300) NOT NULL,
              content CLOB NOT NULL,
              author_id VARCHAR(255) NULL,
              author_name VARCHAR(50) NOT NULL,
              password_hash VARCHAR(255) NULL,
              admin_author VARCHAR(100) NULL,
              author_ip VARCHAR(64) NULL,
              view_count INT NOT NULL DEFAULT 0,
              pinned BOOLEAN NOT NULL DEFAULT FALSE,
              deleted BOOLEAN NOT NULL DEFAULT FALSE,
              insert_dts TIMESTAMP NULL,
              update_dts TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE board_comment (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              post_id BIGINT NOT NULL,
              content VARCHAR(1000) NOT NULL,
              author_id VARCHAR(255) NULL,
              author_name VARCHAR(50) NOT NULL,
              password_hash VARCHAR(255) NULL,
              admin_author VARCHAR(100) NULL,
              author_ip VARCHAR(64) NULL,
              deleted BOOLEAN NOT NULL DEFAULT FALSE,
              insert_dts TIMESTAMP NULL,
              update_dts TIMESTAMP NULL
            )
            """);
        writer = new BoardSystemWriter(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void 글을_넣고_id_를_돌려준다() {
        PostRef ref = writer.ensurePost(notice("[공지] 안내", true)).orElseThrow();

        assertThat(ref.created()).isTrue();
        assertThat(ref.id()).isPositive();

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM board_post WHERE id = ?", ref.id());
        assertThat(row.get("BOARD_TYPE")).isEqualTo("NOTICE");
        assertThat(row.get("TITLE")).isEqualTo("[공지] 안내");
        assertThat(row.get("AUTHOR_NAME")).isEqualTo("관리자");
        assertThat(row.get("ADMIN_AUTHOR")).isEqualTo("system-sample");
        // 로그인 사용자도, 익명(비밀번호)도 아니다 — 그래서 관리자만 수정·삭제할 수 있다.
        assertThat(row.get("AUTHOR_ID")).isNull();
        assertThat(row.get("PASSWORD_HASH")).isNull();
        assertThat(row.get("PINNED")).isEqualTo(true);
        assertThat(row.get("VIEW_COUNT")).isEqualTo(0);
        assertThat(row.get("INSERT_DTS")).isNotNull();
    }

    @Test
    void 같은_글은_두번_넣지_않고_같은_id_를_돌려준다() {
        PostRef first = writer.ensurePost(notice("[공지] 안내", false)).orElseThrow();
        PostRef second = writer.ensurePost(notice("[공지] 안내", false)).orElseThrow();

        assertThat(second.created()).isFalse();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(countPosts()).isEqualTo(1);
    }

    @Test
    void 제목_접두사만_같아도_이미_있는_글로_본다() {
        // 패치노트가 이 방식으로 중복을 막는다. 파일에서 제목을 고쳐도 다시 올라가지 않아야 한다.
        writer.ensurePost(new BoardSystemPost(BoardType.NOTICE, "[패치노트] 2026-09-01",
            "[패치노트] 2026-09-01 처음 쓴 제목", "- 내용", "관리자", "system-patch-note", false, now()));

        PostRef again = writer.ensurePost(new BoardSystemPost(BoardType.NOTICE, "[패치노트] 2026-09-01",
            "[패치노트] 2026-09-01 고친 제목", "- 내용", "관리자", "system-patch-note", false, now())).orElseThrow();

        assertThat(again.created()).isFalse();
        assertThat(countPosts()).isEqualTo(1);
    }

    @Test
    void 관리자가_지운_글은_되살리지_않는다() {
        long id = writer.ensurePost(notice("[공지] 안내", false)).orElseThrow().id();
        jdbcTemplate.update("UPDATE board_post SET deleted = TRUE WHERE id = ?", id);

        PostRef again = writer.ensurePost(notice("[공지] 안내", false)).orElseThrow();

        assertThat(again.created()).isFalse();
        assertThat(countPosts()).isEqualTo(1);
    }

    @Test
    void 게시판이_다르면_다른_글이다() {
        writer.ensurePost(notice("같은 제목", false));
        PostRef free = writer.ensurePost(new BoardSystemPost(BoardType.FREE, "같은 제목", "같은 제목",
            "- 내용", "익명", "system-sample", false, now())).orElseThrow();

        assertThat(free.created()).isTrue();
        assertThat(countPosts()).isEqualTo(2);
    }

    @Test
    void 댓글은_같은_내용을_두번_달지_않는다() {
        long id = writer.ensurePost(notice("[공지] 안내", false)).orElseThrow().id();

        assertThat(writer.ensureComment(id, "첫 댓글", "익명", null, now())).isTrue();
        assertThat(writer.ensureComment(id, "첫 댓글", "익명", null, now())).isFalse();
        assertThat(writer.ensureComment(id, "다른 댓글", "익명", null, now())).isTrue();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM board_comment WHERE post_id = ?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void 제목이_상한을_넘으면_잘라서_넣는다() {
        String longTitle = "가".repeat(400);
        long id = writer.ensurePost(new BoardSystemPost(BoardType.NOTICE, longTitle, longTitle,
            "- 내용", "관리자", "system-sample", false, now())).orElseThrow().id();

        String stored = jdbcTemplate.queryForObject(
            "SELECT title FROM board_post WHERE id = ?", String.class, id);
        assertThat(stored).hasSize(300);
    }

    @Test
    void 테이블이_없으면_예외대신_빈값() {
        jdbcTemplate.execute("DROP TABLE board_post");
        jdbcTemplate.execute("DROP TABLE board_comment");

        assertThat(writer.ensurePost(notice("[공지] 안내", false))).isEmpty();
        assertThat(writer.ensureComment(1L, "댓글", "익명", null, now())).isFalse();
    }

    @Test
    void adminAuthor_가_없으면_사용자_글로_저장된다() {
        long id = writer.ensurePost(new BoardSystemPost(BoardType.FREE, "제목", "제목", "- 내용",
            "익명", null, false, now())).orElseThrow().id();

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM board_post WHERE id = ?", id);
        // adminAuthor 가 비어 있으면 화면에 관리자 뱃지가 붙지 않는다(BoardPostSummaryDto.writtenByAdmin).
        assertThat(row.get("ADMIN_AUTHOR")).isNull();
        assertThat(row.get("AUTHOR_NAME")).isEqualTo("익명");
    }

    private BoardSystemPost notice(String title, boolean pinned) {
        return new BoardSystemPost(BoardType.NOTICE, title, title, "- 내용",
            "관리자", "system-sample", pinned, now());
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 9, 1, 10, 0);
    }

    private int countPosts() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM board_post", Integer.class);
        return count == null ? 0 : count;
    }
}
