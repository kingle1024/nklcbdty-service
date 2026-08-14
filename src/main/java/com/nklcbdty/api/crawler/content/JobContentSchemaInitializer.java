package com.nklcbdty.api.crawler.content;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * {@code spring.jpa.hibernate.ddl-auto=none} 이라 JPA 가 테이블을 만들지 않는다. 기동 시 직접 만든다.
 *
 * <p>{@code CREATE TABLE IF NOT EXISTS} 만 두지 않는 이유: 이 스키마({@code travel})는 다른
 * 프로젝트와 공유라, 같은 이름의 테이블이 다른 모양으로 이미 있으면 CREATE 는 조용히 넘어가고
 * 컬럼이 없는 채로 뜬다(2026-08-13 자유게시판 500 이 이 경우였다). 컬럼을 하나씩 확인해 채운다.</p>
 */
@Slf4j
@Component
public class JobContentSchemaInitializer implements ApplicationRunner {

    private static final String CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS job_content (" +
        "  job_id BIGINT NOT NULL," +
        "  company_cd VARCHAR(50) NULL," +
        "  content MEDIUMTEXT NULL," +
        "  content_html MEDIUMTEXT NULL," +
        "  content_hash CHAR(64) NULL," +
        "  source VARCHAR(60) NULL," +
        "  fail_reason VARCHAR(300) NULL," +
        "  fetched_at DATETIME NULL," +
        "  updated_at DATETIME NULL," +
        "  PRIMARY KEY (job_id)," +
        "  KEY idx_job_content_fetched_at (fetched_at)" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    /** 이미 있는 테이블에 빠진 컬럼을 채운다. MariaDB 의 ADD COLUMN IF NOT EXISTS 를 쓴다. */
    private static final List<String> ADD_COLUMNS = List.of(
        "ALTER TABLE job_content ADD COLUMN IF NOT EXISTS company_cd VARCHAR(50) NULL",
        "ALTER TABLE job_content ADD COLUMN IF NOT EXISTS content MEDIUMTEXT NULL",
        "ALTER TABLE job_content ADD COLUMN IF NOT EXISTS content_html MEDIUMTEXT NULL",
        "ALTER TABLE job_content ADD COLUMN IF NOT EXISTS content_hash CHAR(64) NULL",
        "ALTER TABLE job_content ADD COLUMN IF NOT EXISTS source VARCHAR(60) NULL",
        "ALTER TABLE job_content ADD COLUMN IF NOT EXISTS fail_reason VARCHAR(300) NULL",
        "ALTER TABLE job_content ADD COLUMN IF NOT EXISTS fetched_at DATETIME NULL",
        "ALTER TABLE job_content ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL"
    );

    private final JdbcTemplate jdbcTemplate;

    public JobContentSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(CREATE_TABLE);
        } catch (Exception e) {
            log.error("[JobContent] job_content 테이블 생성 실패: {}", e.getMessage(), e);
            return;
        }

        for (String ddl : ADD_COLUMNS) {
            try {
                jdbcTemplate.execute(ddl);
            } catch (Exception e) {
                // 한 컬럼이 실패해도 나머지는 채운다. 어느 컬럼인지 남겨야 진단이 된다.
                log.error("[JobContent] 컬럼 보정 실패: {} - {}", ddl, e.getMessage());
            }
        }
        log.info("[JobContent] job_content 테이블 확인/생성 완료");
    }
}
