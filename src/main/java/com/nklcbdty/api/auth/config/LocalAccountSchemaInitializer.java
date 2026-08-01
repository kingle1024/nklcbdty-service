package com.nklcbdty.api.auth.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * spring.jpa.hibernate.ddl-auto=none 이므로 local_account 테이블을 직접 생성한다("테이블 없으면 생성").
 */
@Slf4j
@Component
public class LocalAccountSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public LocalAccountSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        final String ddl =
            "CREATE TABLE IF NOT EXISTS local_account (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  email VARCHAR(255) NOT NULL," +
            "  password_hash VARCHAR(255) NOT NULL," +
            "  user_id VARCHAR(100) NOT NULL," +
            "  insert_dts DATETIME NULL," +
            "  PRIMARY KEY (id)," +
            "  UNIQUE KEY uk_local_account_email (email)," +
            "  UNIQUE KEY uk_local_account_user_id (user_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try {
            jdbcTemplate.execute(ddl);
            log.info("[LocalAuth] local_account 테이블 확인/생성 완료");
        } catch (Exception e) {
            log.error("[LocalAuth] local_account 테이블 생성 실패: {}", e.getMessage(), e);
        }
    }
}
