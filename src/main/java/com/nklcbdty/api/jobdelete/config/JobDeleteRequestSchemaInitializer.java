package com.nklcbdty.api.jobdelete.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * spring.jpa.hibernate.ddl-auto=none 이므로 JPA 가 테이블을 만들지 않는다.
 * 앱 기동 시 job_delete_request 테이블이 없으면 생성한다("테이블 없으면 생성").
 */
@Slf4j
@Component
public class JobDeleteRequestSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public JobDeleteRequestSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        final String ddl =
            "CREATE TABLE IF NOT EXISTS job_delete_request (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  job_id BIGINT NOT NULL," +
            "  anno_id VARCHAR(255) NULL," +
            "  anno_subject VARCHAR(500) NULL," +
            "  company_cd VARCHAR(255) NULL," +
            "  sys_company_cd_nm VARCHAR(255) NULL," +
            "  reason VARCHAR(1000) NULL," +
            "  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
            "  requester_id VARCHAR(255) NULL," +
            "  requester_ip VARCHAR(64) NULL," +
            "  processed_by VARCHAR(255) NULL," +
            "  insert_dts DATETIME NULL," +
            "  process_dts DATETIME NULL," +
            "  PRIMARY KEY (id)," +
            "  KEY idx_jdr_status (status)," +
            "  KEY idx_jdr_job_id (job_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try {
            jdbcTemplate.execute(ddl);
            log.info("[JobDeleteRequest] job_delete_request 테이블 확인/생성 완료");
        } catch (Exception e) {
            log.error("[JobDeleteRequest] job_delete_request 테이블 생성 실패: {}", e.getMessage(), e);
        }
    }
}
