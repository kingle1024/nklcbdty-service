package com.nklcbdty.api.board.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 시스템이 게시판에 글/댓글을 직접 넣는다. 샘플 시드와 패치노트 자동 공지가 쓴다.
 *
 * <p>왜 {@link BoardService} 를 쓰지 않는가: 작성 시각을 항목마다 다르게 지정해야 하고
 * ({@code @CreationTimestamp} 는 항상 now() 다), 같은 글을 두 번 넣지 않는 판정이 필요하고,
 * 관리자 토큰 없이 기동 중에 실행되기 때문이다. 그래서 JdbcTemplate 으로 INSERT 한다.</p>
 *
 * <p>이 클래스의 실패는 로그만 남기고 삼킨다. 게시판 샘플/공지 때문에 기동이 멈출 이유는 없고,
 * 다음 기동에서 다시 시도된다.</p>
 */
@Slf4j
@Component
public class BoardSystemWriter {

    /** BoardService 와 같은 상한. 넘으면 자른다 — 길다고 기동 중에 예외를 던지지 않는다. */
    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MAX_CONTENT_LENGTH = 20_000;
    private static final int MAX_AUTHOR_NAME_LENGTH = 50;
    private static final int MAX_COMMENT_LENGTH = 1_000;

    private static final String INSERT_POST =
        "INSERT INTO board_post "
        + "(board_type, title, content, author_id, author_name, password_hash, admin_author, author_ip, "
        + " view_count, pinned, deleted, insert_dts, update_dts) "
        + "VALUES (?, ?, ?, NULL, ?, NULL, ?, NULL, 0, ?, 0, ?, ?)";

    private static final String INSERT_COMMENT =
        "INSERT INTO board_comment "
        + "(post_id, content, author_id, author_name, password_hash, admin_author, author_ip, "
        + " deleted, insert_dts, update_dts) "
        + "VALUES (?, ?, NULL, ?, NULL, ?, NULL, 0, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public BoardSystemWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** {@link #ensurePost} 결과. {@code created=false} 면 이미 있던 글이다. */
    public record PostRef(long id, boolean created) {
    }

    /**
     * 글이 없으면 넣고, 있으면 그대로 둔다. 어느 경우든 그 글의 id 를 돌려준다(댓글을 달 수 있게).
     * 실패하면 빈 값이다.
     *
     * <p>삭제된 글도 "있는 것"으로 본다. 관리자가 지운 샘플/공지를 다음 배포에서 되살리면 안 된다.</p>
     */
    public Optional<PostRef> ensurePost(BoardSystemPost post) {
        try {
            OptionalLong existing = findIdByDedupeKey(post.boardType().name(), post.dedupeKey());
            if (existing.isPresent()) {
                return Optional.of(new PostRef(existing.getAsLong(), false));
            }
            return Optional.of(new PostRef(insert(post), true));
        } catch (Exception e) {
            log.error("[Board] 시스템 글 저장 실패 boardType={} title={} : {}",
                post.boardType(), post.title(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 같은 글에 같은 내용의 댓글이 없으면 넣는다.
     *
     * @param adminAuthor 관리자 댓글이면 감사용 계정, 사용자 댓글이면 null.
     *                    이 값이 있으면 화면에 관리자 뱃지가 붙는다(BoardCommentDto.writtenByAdmin).
     */
    public boolean ensureComment(long postId, String content, String authorName, String adminAuthor,
                                 LocalDateTime writtenAt) {
        String text = clamp(content, MAX_COMMENT_LENGTH);
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM board_comment WHERE post_id = ? AND content = ?",
                Integer.class, postId, text);
            if (count != null && count > 0) {
                return false;
            }
            jdbcTemplate.update(INSERT_COMMENT, postId, text,
                clamp(authorName, MAX_AUTHOR_NAME_LENGTH), adminAuthor,
                Timestamp.valueOf(writtenAt), Timestamp.valueOf(writtenAt));
            return true;
        } catch (Exception e) {
            log.error("[Board] 시스템 댓글 저장 실패 postId={} : {}", postId, e.getMessage(), e);
            return false;
        }
    }

    private OptionalLong findIdByDedupeKey(String boardType, String dedupeKey) {
        // dedupeKey 에는 LIKE 와일드카드(%, _)를 쓰지 않는다. 접두사가 그대로 비교된다.
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT id FROM board_post WHERE board_type = ? AND title LIKE ? ORDER BY id LIMIT 1",
            Long.class, boardType, dedupeKey + "%");
        return ids.isEmpty() ? OptionalLong.empty() : OptionalLong.of(ids.get(0));
    }

    private long insert(BoardSystemPost post) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_POST, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, post.boardType().name());
            ps.setString(2, clamp(post.title(), MAX_TITLE_LENGTH));
            ps.setString(3, clamp(post.content(), MAX_CONTENT_LENGTH));
            ps.setString(4, clamp(post.authorName(), MAX_AUTHOR_NAME_LENGTH));
            ps.setString(5, post.adminAuthor());
            ps.setBoolean(6, post.pinned());
            ps.setTimestamp(7, Timestamp.valueOf(post.writtenAt()));
            ps.setTimestamp(8, Timestamp.valueOf(post.writtenAt()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key == null ? -1L : key.longValue();
        log.info("[Board] 시스템 글 등록 boardType={} id={} title={}", post.boardType(), id, post.title());
        return id;
    }

    private String clamp(String value, int maxLength) {
        String text = value == null ? "" : value.strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
