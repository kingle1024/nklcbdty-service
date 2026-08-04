package com.nklcbdty.api.board.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * spring.jpa.hibernate.ddl-auto=none 이므로 JPA 가 테이블을 만들지 않는다.
 * 앱 기동 시 게시판 테이블이 없으면 생성한다(JobDeleteRequestSchemaInitializer 와 같은 방식).
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
            "  KEY idx_bp_deleted_id (deleted, id)," +
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
    }
}
