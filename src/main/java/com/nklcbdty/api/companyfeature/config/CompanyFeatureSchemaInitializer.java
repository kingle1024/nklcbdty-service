package com.nklcbdty.api.companyfeature.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * spring.jpa.hibernate.ddl-auto=none 이므로 JPA 가 테이블을 만들지 않는다.
 * 앱 기동 시 재택/복지 테이블(welfare_category / company_feature)이 없으면 만들고,
 * 복지 카테고리가 하나도 없으면 기본 카테고리를 넣는다.
 *
 * <p>company_feature 는 nklcbdty-batch 가 채운다. 배치는 테이블을 만들지 않으므로 이 서비스가
 * 먼저 한 번 떠 있어야 한다.</p>
 */
@Slf4j
@Component
public class CompanyFeatureSchemaInitializer implements ApplicationRunner {

    /**
     * 기본 복지 카테고리: 코드, 이름, 키워드(줄바꿈 구분).
     *
     * <p>업무 설명에도 나오는 말은 혜택 문구로 좁혔다. 실제 공고에서 "컨퍼런스콜 통역"(통번역 직무)이
     * 교육비로, "건강검진 실시"(보건관리자 직무)가 건강 복지로 잡혔다.</p>
     */
    static final List<String[]> DEFAULT_CATEGORIES = List.of(
        new String[] {"MEAL", "식대", "식대\n식비\n식사 지원\n식사 제공\n사내 식당\n구내식당\n카페테리아\n간식"},
        new String[] {"EQUIPMENT", "장비지원", "장비 지원\n업무 장비\n최신 장비\n맥북\nMacBook\n듀얼 모니터\n노트북 지원"},
        new String[] {"EDUCATION", "교육비", "교육비\n도서 구입\n도서 지원\n도서비\n컨퍼런스 참가\n컨퍼런스 지원\n세미나 참가\n자기계발비\n학습 지원"},
        new String[] {"VACATION", "휴가", "리프레시 휴가\n안식 휴가\n안식휴가\n자율 휴가\n유급 휴가\n장기근속 휴가\n휴가비"},
        new String[] {"HEALTH", "건강", "건강검진 지원\n종합건강검진\n종합 검진\n건강검진비\n의료비 지원\n단체 보험\n단체상해보험\n실손보험\n심리 상담\n피트니스\n헬스장"}
    );

    private final JdbcTemplate jdbcTemplate;

    public CompanyFeatureSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        final String categoryDdl =
            "CREATE TABLE IF NOT EXISTS welfare_category (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  code VARCHAR(30) NOT NULL," +
            "  name VARCHAR(50) NOT NULL," +
            "  keywords TEXT NOT NULL," +
            "  sort_order INT NOT NULL DEFAULT 0," +
            "  active TINYINT(1) NOT NULL DEFAULT 1," +
            "  insert_dts DATETIME NULL," +
            "  update_dts DATETIME NULL," +
            "  PRIMARY KEY (id)," +
            "  UNIQUE KEY uk_welfare_category_code (code)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        // 공고 한 건당 (특징 종류, 코드) 한 행. 근무형태가 늘어도 feature_code 값만 늘어난다.
        final String featureDdl =
            "CREATE TABLE IF NOT EXISTS company_feature (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  company_cd VARCHAR(30) NOT NULL," +
            "  feature_type VARCHAR(20) NOT NULL," +
            "  feature_code VARCHAR(30) NOT NULL," +
            "  job_id BIGINT NOT NULL," +
            "  anno_subject VARCHAR(500) NULL," +
            "  sys_company_nm VARCHAR(255) NULL," +
            "  job_detail_link VARCHAR(1000) NULL," +
            "  matched_keyword VARCHAR(100) NOT NULL," +
            "  snippet VARCHAR(500) NOT NULL," +
            "  detected_dts DATETIME NOT NULL," +
            "  PRIMARY KEY (id)," +
            "  UNIQUE KEY uk_company_feature_job (job_id, feature_type, feature_code)," +
            "  KEY idx_company_feature_lookup (feature_type, feature_code, company_cd)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try {
            jdbcTemplate.execute(categoryDdl);
            jdbcTemplate.execute(featureDdl);
            log.info("[CompanyFeature] welfare_category / company_feature 테이블 확인/생성 완료");
            seedDefaultCategories();
        } catch (Exception e) {
            log.error("[CompanyFeature] 테이블 생성 실패: {}", e.getMessage(), e);
        }
    }

    /** 처음 한 번만. 관리자가 전부 비활성화해도 행은 남으므로 다시 들어가지 않는다. */
    private void seedDefaultCategories() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM welfare_category", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        int order = 1;
        for (String[] c : DEFAULT_CATEGORIES) {
            jdbcTemplate.update(
                "INSERT INTO welfare_category (code, name, keywords, sort_order, active, insert_dts, update_dts)" +
                " VALUES (?, ?, ?, ?, 1, NOW(), NOW())",
                c[0], c[1], c[2], order++);
        }
        log.info("[CompanyFeature] 기본 복지 카테고리 {}개 생성", DEFAULT_CATEGORIES.size());
    }
}
