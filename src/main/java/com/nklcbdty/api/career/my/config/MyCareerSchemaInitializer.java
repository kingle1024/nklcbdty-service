package com.nklcbdty.api.career.my.config;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * {@code ddl-auto=none} 이라 JPA 가 표를 만들지 않는다. 기동 때 my_career_entry 가 없으면 만든다.
 *
 * <p>운영 DB(travel 스키마)는 다른 프로젝트와 함께 쓴다. 같은 이름의 표가 다른 모양으로 있으면
 * CREATE TABLE IF NOT EXISTS 가 조용히 넘어가므로, 빠진 칼럼을 채우고 마지막에 실제로 조회해 본다
 * ({@code MyCalendarSchemaInitializer} 와 같은 방식). 실패해도 기동은 막지 않는다 — 경력 화면 하나
 * 때문에 서비스 전체를 내릴 이유는 없고, 원인은 로그로 남긴다.
 */
@Slf4j
@Component
public class MyCareerSchemaInitializer implements ApplicationRunner {

    /** 엔티티가 쓰는 칼럼. 뒤늦게 붙일 수 있어야 하므로 NOT NULL 에는 DEFAULT 를 준다 */
    static final Map<String, String> COLUMNS = new LinkedHashMap<>();
    static {
        COLUMNS.put("user_id", "VARCHAR(255) NOT NULL DEFAULT ''");
        COLUMNS.put("entry_type", "VARCHAR(20) NOT NULL DEFAULT 'etc'");
        COLUMNS.put("company", "VARCHAR(100) NULL");
        COLUMNS.put("title", "VARCHAR(300) NOT NULL DEFAULT ''");
        COLUMNS.put("team", "VARCHAR(100) NULL");
        COLUMNS.put("role", "VARCHAR(100) NULL");
        COLUMNS.put("started_on", "DATE NULL");
        COLUMNS.put("ended_on", "DATE NULL");
        COLUMNS.put("summary", "TEXT NULL");
        COLUMNS.put("description", "TEXT NULL");
        COLUMNS.put("achievements", "TEXT NULL");
        COLUMNS.put("tech_stack", "VARCHAR(300) NULL");
        COLUMNS.put("tags", "VARCHAR(300) NULL");
        COLUMNS.put("reference_links", "TEXT NULL");
        COLUMNS.put("created_at", "DATETIME NULL");
        COLUMNS.put("updated_at", "DATETIME NULL");
    }

    /** 남의 긴 트랜잭션이 락을 잡고 있어도 기동이 멈추지 않게 하는 상한 */
    private static final int LOCK_WAIT_SECONDS = 10;
    private static final int QUERY_TIMEOUT_SECONDS = 20;

    private final JdbcTemplate jdbcTemplate;

    public MyCareerSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS my_career_entry (")
            .append("id BIGINT NOT NULL AUTO_INCREMENT,");
        COLUMNS.forEach((column, definition) -> ddl.append(column).append(' ').append(definition).append(','));
        // 유일한 조회 패턴이 "한 사용자의 전부" 다
        ddl.append("PRIMARY KEY (id), KEY idx_my_career_user (user_id)")
            .append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        try {
            execute(ddl.toString());
        } catch (Exception e) {
            log.error("[MyCareer] my_career_entry 테이블 생성 실패: {}", e.getMessage(), e);
        }

        // 위 CREATE 는 표가 이미 있으면 아무것도 안 한다. MariaDB 10.0+ 라 여러 번 실행해도 안전하다
        COLUMNS.forEach((column, definition) -> {
            try {
                execute("ALTER TABLE my_career_entry ADD COLUMN IF NOT EXISTS " + column + " " + definition);
            } catch (Exception e) {
                log.error("[MyCareer] my_career_entry.{} 칼럼 추가 실패: {}", column, e.getMessage(), e);
            }
        });

        try {
            jdbcTemplate.queryForList(
                "SELECT id, " + String.join(", ", COLUMNS.keySet()) + " FROM my_career_entry LIMIT 1");
            log.info("[MyCareer] my_career_entry 스키마 확인 완료");
        } catch (Exception e) {
            log.error("[MyCareer] my_career_entry 스키마가 코드와 맞지 않는다 — 내 경력 API 가 500 을 낸다: {}",
                e.getMessage(), e);
        }
    }

    /** 같은 커넥션에서 락 대기 상한을 건 뒤 DDL 을 실행한다(이유는 MyCalendarSchemaInitializer 참고) */
    private void execute(String ddl) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                try {
                    statement.execute("SET SESSION lock_wait_timeout = " + LOCK_WAIT_SECONDS);
                } catch (SQLException e) {
                    log.debug("[MyCareer] lock_wait_timeout 설정 실패(무시): {}", e.getMessage());
                }
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.execute(ddl);
            }
            return null;
        });
    }
}
