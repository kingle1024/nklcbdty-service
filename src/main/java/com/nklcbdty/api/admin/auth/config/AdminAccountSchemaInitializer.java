package com.nklcbdty.api.admin.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.nklcbdty.api.admin.auth.repository.AdminAccountRepository;
import com.nklcbdty.api.admin.auth.vo.AdminAccount;

import lombok.extern.slf4j.Slf4j;

/**
 * spring.jpa.hibernate.ddl-auto=none 이므로 admin_account 테이블을 직접 생성한다("테이블 없으면 생성").
 * 또한 관리자 계정이 하나도 없으면 초기 관리자(admin.init.username/password)를 시드한다.
 */
@Slf4j
@Component
public class AdminAccountSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${admin.init.username:admin}")
    private String initUsername;

    @Value("${admin.init.password:admin1234}")
    private String initPassword;

    public AdminAccountSchemaInitializer(JdbcTemplate jdbcTemplate, AdminAccountRepository adminAccountRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminAccountRepository = adminAccountRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        final String ddl =
            "CREATE TABLE IF NOT EXISTS admin_account (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  username VARCHAR(100) NOT NULL," +
            "  password_hash VARCHAR(255) NOT NULL," +
            "  display_name VARCHAR(100) NULL," +
            "  insert_dts DATETIME NULL," +
            "  PRIMARY KEY (id)," +
            "  UNIQUE KEY uk_admin_username (username)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try {
            jdbcTemplate.execute(ddl);
            log.info("[AdminAccount] admin_account 테이블 확인/생성 완료");
        } catch (Exception e) {
            log.error("[AdminAccount] admin_account 테이블 생성 실패: {}", e.getMessage(), e);
            return;
        }

        // 관리자 계정이 하나도 없으면 초기 관리자 시드
        try {
            if (adminAccountRepository.count() == 0) {
                AdminAccount admin = new AdminAccount();
                admin.setUsername(initUsername);
                admin.setPasswordHash(passwordEncoder.encode(initPassword));
                admin.setDisplayName("관리자");
                adminAccountRepository.save(admin);
                log.warn("[AdminAccount] 초기 관리자 계정 생성: username='{}'. ★보안을 위해 즉시 비밀번호를 변경하세요★", initUsername);
            }
        } catch (Exception e) {
            log.error("[AdminAccount] 초기 관리자 시드 실패: {}", e.getMessage(), e);
        }
    }
}
