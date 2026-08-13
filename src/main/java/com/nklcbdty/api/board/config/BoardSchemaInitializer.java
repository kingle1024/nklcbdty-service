package com.nklcbdty.api.board.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * spring.jpa.hibernate.ddl-auto=none 이므로 JPA 가 테이블을 만들지 않는다.
 * 앱 기동 시 게시판 테이블이 없으면 생성한다(JobDeleteRequestSchemaInitializer 와 같은 방식).
 *
 * 주의: 이 DB 는 다른 프로젝트와 함께 쓰는 스키마라 board_post 가 다른 모양으로 이미 있을 수 있다.
 * 그 경우 CREATE TABLE IF NOT EXISTS 는 아무 일도 하지 않아 컬럼이 모자란 채로 남고, 조회 시
 * "Unknown column" 으로 500 이 난다. 그래서 생성 후 필요한 컬럼을 개별적으로 채워 넣는다.
 */
@Slf4j
@Component
public class BoardSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public BoardSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        final String postDdl =
            "CREATE TABLE IF NOT EXISTS board_post (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  board_type VARCHAR(20) NOT NULL DEFAULT 'FREE'," +
            "  title VARCHAR(200) NOT NULL," +
            "  content TEXT NOT NULL," +
            "  author_id VARCHAR(100) NOT NULL," +
            "  author_name VARCHAR(100) NULL," +
            "  view_count INT NOT NULL DEFAULT 0," +
            "  comment_count INT NOT NULL DEFAULT 0," +
            "  deleted TINYINT(1) NOT NULL DEFAULT 0," +
            "  insert_dts DATETIME NULL," +
            "  update_dts DATETIME NULL," +
            "  PRIMARY KEY (id)," +
            "  KEY idx_bp_type_deleted_id (board_type, deleted, id)," +
            "  KEY idx_bp_author (author_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        final String commentDdl =
            "CREATE TABLE IF NOT EXISTS board_comment (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  post_id BIGINT NOT NULL," +
            "  content VARCHAR(1000) NOT NULL," +
            "  author_id VARCHAR(100) NOT NULL," +
            "  author_name VARCHAR(100) NULL," +
            "  deleted TINYINT(1) NOT NULL DEFAULT 0," +
            "  insert_dts DATETIME NULL," +
            "  update_dts DATETIME NULL," +
            "  PRIMARY KEY (id)," +
            "  KEY idx_bc_post (post_id, deleted, id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try {
            jdbcTemplate.execute(postDdl);
            jdbcTemplate.execute(commentDdl);
            log.info("[Board] board_post / board_comment 테이블 확인/생성 완료");
        } catch (Exception e) {
            log.error("[Board] 게시판 테이블 생성 실패: {}", e.getMessage(), e);
        }

        // 이미 있던 테이블에 이 코드가 쓰는 컬럼이 빠져 있으면 채운다.
        // (MariaDB 10.0+ 의 ADD COLUMN IF NOT EXISTS 라 여러 번 실행해도 안전하다)
        addColumnIfMissing("board_post", "board_type", "VARCHAR(20) NOT NULL DEFAULT 'FREE'");
        addColumnIfMissing("board_post", "comment_count", "INT NOT NULL DEFAULT 0");

        verifyColumns();
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + definition);
        } catch (Exception e) {
            log.error("[Board] {}.{} 컬럼 추가 실패: {}", table, column, e.getMessage(), e);
        }
    }

    /**
     * 실제로 조회가 되는지 확인한다. 여기서 실패하면 게시판 API 가 500 을 내므로,
     * 조용히 넘어가지 말고 기동 로그에 남겨 배포 직후 바로 알아채도록 한다.
     */
    private void verifyColumns() {
        try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM board_post WHERE board_type = 'FREE' AND deleted = false", Integer.class);
            log.info("[Board] 게시판 스키마 확인 완료");
        } catch (Exception e) {
            log.error("[Board] 게시판 스키마가 코드와 맞지 않는다 — 게시판 API 가 실패한다: {}", e.getMessage(), e);
        }
    }
}
