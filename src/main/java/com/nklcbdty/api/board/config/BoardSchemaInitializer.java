package com.nklcbdty.api.board.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * spring.jpa.hibernate.ddl-auto=none 이므로 JPA 가 테이블을 만들지 않는다.
 * 앱 기동 시 게시판 테이블(board_post / board_comment)이 없으면 생성한다.
 *
 * 주의: 이 DB(travel 스키마)는 다른 프로젝트와 함께 쓰기 때문에 board_post / board_comment 가
 * 다른 모양으로 이미 있을 수 있다. 그러면 CREATE TABLE IF NOT EXISTS 는 아무 일도 하지 않고
 * 넘어가서 컬럼이 모자란 채로 남고, 기동은 성공하는데 게시판 API 만 500 이 난다. 2026-08-13 에
 * 실제로 그렇게 장애가 났다(Unknown column 'comment_count' / Field 'board_type' doesn't have
 * a default value). 그래서 생성 뒤에 이 코드가 쓰는 컬럼을 하나씩 채우고, 마지막에 자기점검을 한다.
 */
@Slf4j
@Component
public class BoardSchemaInitializer implements ApplicationRunner {

    /**
     * BoardPost 엔티티가 쓰는 컬럼. 이미 있는 테이블에 뒤늦게 붙일 수 있어야 하므로
     * NOT NULL 인 컬럼에는 반드시 DEFAULT 를 준다(기존 행을 채울 값이 있어야 한다).
     */
    private static final Map<String, String> POST_COLUMNS = new LinkedHashMap<>();
    static {
        POST_COLUMNS.put("board_type", "VARCHAR(20) NOT NULL DEFAULT 'FREE'");
        POST_COLUMNS.put("title", "VARCHAR(300) NOT NULL DEFAULT ''");
        POST_COLUMNS.put("content", "LONGTEXT NULL");
        POST_COLUMNS.put("author_id", "VARCHAR(255) NULL");
        POST_COLUMNS.put("author_name", "VARCHAR(50) NOT NULL DEFAULT ''");
        POST_COLUMNS.put("password_hash", "VARCHAR(255) NULL");
        POST_COLUMNS.put("admin_author", "VARCHAR(100) NULL");
        POST_COLUMNS.put("author_ip", "VARCHAR(64) NULL");
        POST_COLUMNS.put("view_count", "INT NOT NULL DEFAULT 0");
        POST_COLUMNS.put("pinned", "TINYINT(1) NOT NULL DEFAULT 0");
        POST_COLUMNS.put("deleted", "TINYINT(1) NOT NULL DEFAULT 0");
        POST_COLUMNS.put("insert_dts", "DATETIME NULL");
        POST_COLUMNS.put("update_dts", "DATETIME NULL");
    }

    /** BoardComment 엔티티가 쓰는 컬럼. */
    private static final Map<String, String> COMMENT_COLUMNS = new LinkedHashMap<>();
    static {
        COMMENT_COLUMNS.put("post_id", "BIGINT NOT NULL DEFAULT 0");
        COMMENT_COLUMNS.put("content", "VARCHAR(1000) NOT NULL DEFAULT ''");
        COMMENT_COLUMNS.put("author_id", "VARCHAR(255) NULL");
        COMMENT_COLUMNS.put("author_name", "VARCHAR(50) NOT NULL DEFAULT ''");
        COMMENT_COLUMNS.put("password_hash", "VARCHAR(255) NULL");
        COMMENT_COLUMNS.put("admin_author", "VARCHAR(100) NULL");
        COMMENT_COLUMNS.put("author_ip", "VARCHAR(64) NULL");
        COMMENT_COLUMNS.put("deleted", "TINYINT(1) NOT NULL DEFAULT 0");
        COMMENT_COLUMNS.put("insert_dts", "DATETIME NULL");
        COMMENT_COLUMNS.put("update_dts", "DATETIME NULL");
    }

    private final JdbcTemplate jdbcTemplate;

    public BoardSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        final String postDdl =
            "CREATE TABLE IF NOT EXISTS board_post (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  board_type VARCHAR(20) NOT NULL," +
            "  title VARCHAR(300) NOT NULL," +
            "  content LONGTEXT NOT NULL," +
            "  author_id VARCHAR(255) NULL," +
            "  author_name VARCHAR(50) NOT NULL," +
            "  password_hash VARCHAR(255) NULL," +
            "  admin_author VARCHAR(100) NULL," +
            "  author_ip VARCHAR(64) NULL," +
            "  view_count INT NOT NULL DEFAULT 0," +
            "  pinned TINYINT(1) NOT NULL DEFAULT 0," +
            "  deleted TINYINT(1) NOT NULL DEFAULT 0," +
            "  insert_dts DATETIME NULL," +
            "  update_dts DATETIME NULL," +
            "  PRIMARY KEY (id)," +
            "  KEY idx_board_post_list (board_type, deleted, pinned, insert_dts)," +
            "  KEY idx_board_post_author (author_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        final String commentDdl =
            "CREATE TABLE IF NOT EXISTS board_comment (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  post_id BIGINT NOT NULL," +
            "  content VARCHAR(1000) NOT NULL," +
            "  author_id VARCHAR(255) NULL," +
            "  author_name VARCHAR(50) NOT NULL," +
            "  password_hash VARCHAR(255) NULL," +
            "  admin_author VARCHAR(100) NULL," +
            "  author_ip VARCHAR(64) NULL," +
            "  deleted TINYINT(1) NOT NULL DEFAULT 0," +
            "  insert_dts DATETIME NULL," +
            "  update_dts DATETIME NULL," +
            "  PRIMARY KEY (id)," +
            "  KEY idx_board_comment_post (post_id, deleted, insert_dts)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try {
            jdbcTemplate.execute(postDdl);
            jdbcTemplate.execute(commentDdl);
            log.info("[Board] board_post / board_comment 테이블 확인/생성 완료");
        } catch (Exception e) {
            log.error("[Board] 게시판 테이블 생성 실패: {}", e.getMessage(), e);
        }

        // 위 CREATE 는 IF NOT EXISTS 라, 테이블이 이미 다른 모양으로 있으면 아무 일도 하지 않는다.
        // 이 코드가 쓰는 컬럼을 개별로 채워 넣는다. MariaDB 10.0+ 의 ADD COLUMN IF NOT EXISTS 라
        // 여러 번 기동해도 안전하다.
        POST_COLUMNS.forEach((column, definition) -> addColumnIfMissing("board_post", column, definition));
        COMMENT_COLUMNS.forEach((column, definition) -> addColumnIfMissing("board_comment", column, definition));

        // 예전 자유게시판 스키마의 author_id 는 NOT NULL 이었다. 지금은 익명 글·관리자 글을
        // author_id = NULL 로 넣으므로, STRICT_TRANS_TABLES 아래에서 그대로 두면 작성이 500 이 난다.
        allowNull("board_post", "author_id", "VARCHAR(255) NULL");
        allowNull("board_comment", "author_id", "VARCHAR(255) NULL");

        verifySchema();
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + definition);
        } catch (Exception e) {
            log.error("[Board] {}.{} 컬럼 추가 실패: {}", table, column, e.getMessage(), e);
        }
    }

    /** 이미 NULL 을 허용하면 건드리지 않는다 — 불필요한 ALTER 로 남의 테이블을 잠그지 않기 위함. */
    private void allowNull(String table, String column, String definition) {
        try {
            List<String> nullable = jdbcTemplate.queryForList(
                "SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class, table, column);
            if (nullable.isEmpty() || "YES".equalsIgnoreCase(nullable.get(0))) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition);
            log.warn("[Board] {}.{} 를 NULL 허용으로 바꿨다 — 익명/관리자 글은 author_id 가 없다", table, column);
        } catch (Exception e) {
            log.error("[Board] {}.{} NULL 허용 변경 실패: {}", table, column, e.getMessage(), e);
        }
    }

    /**
     * 스키마가 코드와 맞는지 기동 시 실제로 확인한다.
     *
     * 이 DB 는 다른 프로젝트와 공유하는 스키마라, 위 마이그레이션으로도 못 고치는 어긋남이 남을 수 있다.
     * 그런 상태는 기동에는 아무 영향이 없고 게시판 API 만 500 을 내기 때문에 배포 직후에는 눈치채기
     * 어렵다. 실패해도 기동은 막지 않되(게시판 때문에 서비스 전체를 내릴 이유는 없다) 로그로 알린다.
     */
    private void verifySchema() {
        boolean ok = selectsOk("board_post", POST_COLUMNS.keySet())
                   & selectsOk("board_comment", COMMENT_COLUMNS.keySet())
                   & noBlockingLegacyColumns("board_post", POST_COLUMNS.keySet())
                   & noBlockingLegacyColumns("board_comment", COMMENT_COLUMNS.keySet());
        if (ok) {
            log.info("[Board] 게시판 스키마 확인 완료");
        }
    }

    /** 엔티티가 읽는 컬럼을 전부 넣어 조회해 본다. 하나라도 없으면 Unknown column 으로 실패한다. */
    private boolean selectsOk(String table, Iterable<String> columns) {
        String columnList = String.join(", ", columns);
        try {
            jdbcTemplate.queryForList("SELECT id, " + columnList + " FROM " + table + " LIMIT 1");
            return true;
        } catch (Exception e) {
            log.error("[Board] {} 스키마가 코드와 맞지 않는다 — 게시판 조회 API 가 500 을 낸다: {}",
                table, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 이 코드가 모르는 컬럼 중 "NOT NULL + 기본값 없음" 이 있으면 INSERT 가 통째로 실패한다
     * (STRICT_TRANS_TABLES: Field '...' doesn't have a default value). 남의 프로젝트가 만든
     * 컬럼일 수 있어 마음대로 고치지 않고, 어떤 컬럼 때문인지만 정확히 남긴다.
     */
    private boolean noBlockingLegacyColumns(String table, Iterable<String> knownColumns) {
        try {
            List<String> blocking = new ArrayList<>(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = ? "
                    + "  AND is_nullable = 'NO' AND column_default IS NULL "
                    + "  AND extra NOT LIKE '%auto_increment%'",
                String.class, table));
            knownColumns.forEach(blocking::remove);
            blocking.remove("id");
            if (blocking.isEmpty()) {
                return true;
            }
            log.error("[Board] {} 에 이 코드가 모르는 필수 컬럼이 있다 {} — 글/댓글 작성이 500 을 낸다."
                + " 해당 컬럼에 DEFAULT 를 주거나 NULL 을 허용해야 한다.", table, blocking);
            return false;
        } catch (Exception e) {
            log.error("[Board] {} 컬럼 점검 실패: {}", table, e.getMessage(), e);
            return false;
        }
    }
}
