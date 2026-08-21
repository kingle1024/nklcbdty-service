package com.nklcbdty.api.mycalendar.config;

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
 * 앱 기동 시 my_calendar_entry 가 없으면 생성한다.
 *
 * <p>이 DB(travel 스키마)는 다른 프로젝트와 함께 쓴다. 같은 이름의 테이블이 다른 모양으로 이미
 * 있으면 CREATE TABLE IF NOT EXISTS 는 조용히 넘어가고, 기동은 성공하는데 이 API 만 500 이 난다
 * (2026-08-13 게시판이 실제로 그렇게 죽었다). 그래서 생성 뒤에 이 코드가 쓰는 컬럼을 하나씩 채우고
 * 마지막에 자기점검을 한다. {@code BoardSchemaInitializer} 와 같은 방식이다.</p>
 */
@Slf4j
@Component
public class MyCalendarSchemaInitializer implements ApplicationRunner {

    /**
     * MyCalendarEntry 엔티티가 쓰는 컬럼. 이미 있는 테이블에 뒤늦게 붙일 수 있어야 하므로
     * NOT NULL 인 컬럼에는 반드시 DEFAULT 를 준다(기존 행을 채울 값이 있어야 한다).
     */
    private static final Map<String, String> ENTRY_COLUMNS = new LinkedHashMap<>();
    static {
        ENTRY_COLUMNS.put("user_id", "VARCHAR(255) NOT NULL DEFAULT ''");
        // 상시채용은 마감일이 없어 NULL 로 저장한다. 처음엔 NOT NULL 이었으므로 아래에서 풀어 준다.
        ENTRY_COLUMNS.put("apply_date", "DATE NULL");
        ENTRY_COLUMNS.put("company_name", "VARCHAR(100) NOT NULL DEFAULT ''");
        ENTRY_COLUMNS.put("url", "VARCHAR(1000) NULL");
        ENTRY_COLUMNS.put("memo", "VARCHAR(2000) NULL");
        ENTRY_COLUMNS.put("completed", "TINYINT(1) NOT NULL DEFAULT 0");
        ENTRY_COLUMNS.put("completed_dts", "DATETIME NULL");
        ENTRY_COLUMNS.put("insert_dts", "DATETIME NULL");
        ENTRY_COLUMNS.put("update_dts", "DATETIME NULL");
    }

    /**
     * 이미 있는 테이블에서 NOT NULL 을 풀어야 하는 컬럼. ADD COLUMN IF NOT EXISTS 는 이미 있는
     * 컬럼을 손대지 않으므로, 상시채용을 쓰려면 apply_date 를 따로 MODIFY 해야 한다.
     *
     * <p>MODIFY 는 IF NOT EXISTS 같은 게 없지만 같은 정의로 여러 번 실행해도 결과가 같다.</p>
     */
    private static final Map<String, String> RELAXED_COLUMNS = new LinkedHashMap<>();
    static {
        RELAXED_COLUMNS.put("apply_date", "DATE NULL");
    }

    private final JdbcTemplate jdbcTemplate;

    public MyCalendarSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        final String ddl =
            "CREATE TABLE IF NOT EXISTS my_calendar_entry (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  user_id VARCHAR(255) NOT NULL," +
            // NULL 이면 상시채용(마감일 없음). 달력 칸이 아니라 상시채용 목록에 뜬다.
            "  apply_date DATE NULL," +
            "  company_name VARCHAR(100) NOT NULL," +
            "  url VARCHAR(1000) NULL," +
            "  memo VARCHAR(2000) NULL," +
            "  completed TINYINT(1) NOT NULL DEFAULT 0," +
            "  completed_dts DATETIME NULL," +
            "  insert_dts DATETIME NULL," +
            "  update_dts DATETIME NULL," +
            "  PRIMARY KEY (id)," +
            // 월별 조회가 유일한 조회 패턴이다.
            "  KEY idx_my_calendar_user_date (user_id, apply_date)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try {
            jdbcTemplate.execute(ddl);
            log.info("[MyCalendar] my_calendar_entry 테이블 확인/생성 완료");
        } catch (Exception e) {
            log.error("[MyCalendar] my_calendar_entry 테이블 생성 실패: {}", e.getMessage(), e);
        }

        // 위 CREATE 는 IF NOT EXISTS 라, 테이블이 이미 다른 모양으로 있으면 아무 일도 하지 않는다.
        // MariaDB 10.0+ 의 ADD COLUMN IF NOT EXISTS 라 여러 번 기동해도 안전하다.
        ENTRY_COLUMNS.forEach(this::addColumnIfMissing);

        // 상시채용을 쓰기 전에 만들어진 테이블은 apply_date 가 NOT NULL 이다. 컬럼 추가로는 안 풀린다.
        RELAXED_COLUMNS.forEach(this::relaxNotNull);

        verifySchema();
    }

    private void addColumnIfMissing(String column, String definition) {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE my_calendar_entry ADD COLUMN IF NOT EXISTS " + column + " " + definition);
        } catch (Exception e) {
            log.error("[MyCalendar] my_calendar_entry.{} 컬럼 추가 실패: {}", column, e.getMessage(), e);
        }
    }

    /**
     * NOT NULL 을 풀어 준다. 이미 NULL 을 허용하면 아무것도 하지 않는다 — 매 기동마다 MODIFY 를
     * 던져도 되지만, 큰 테이블에서 헛되게 락을 잡을 이유가 없다.
     */
    private void relaxNotNull(String column, String definition) {
        try {
            if (isNullable(column)) {
                return;
            }
            jdbcTemplate.execute(
                "ALTER TABLE my_calendar_entry MODIFY COLUMN " + column + " " + definition);
            log.info("[MyCalendar] my_calendar_entry.{} 를 NULL 허용으로 바꿨다 — 상시채용 저장 가능", column);
        } catch (Exception e) {
            // 실패하면 상시채용 저장이 500 이 난다. 원인을 바로 알 수 있게 남긴다.
            log.error("[MyCalendar] my_calendar_entry.{} NOT NULL 해제 실패 — 상시채용(날짜 없는 일정) 저장이"
                + " 실패한다: {}", column, e.getMessage(), e);
        }
    }

    /** 컬럼이 NULL 을 허용하는지. 컬럼이 없으면(=방금 추가됐다) 정의대로 NULL 허용이므로 true. */
    private boolean isNullable(String column) {
        final List<String> nullable = jdbcTemplate.queryForList(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'my_calendar_entry' "
                + "  AND column_name = ?",
            String.class, column);
        return nullable.isEmpty() || "YES".equalsIgnoreCase(nullable.get(0));
    }

    /**
     * 스키마가 코드와 맞는지 기동 시 실제로 확인한다. 어긋나 있어도 기동은 막지 않되
     * (캘린더 하나 때문에 서비스 전체를 내릴 이유는 없다) 로그로 알린다.
     */
    private void verifySchema() {
        if (selectsOk() & noBlockingLegacyColumns()) {
            log.info("[MyCalendar] my_calendar_entry 스키마 확인 완료");
        }
    }

    /** 엔티티가 읽는 컬럼을 전부 넣어 조회해 본다. 하나라도 없으면 Unknown column 으로 실패한다. */
    private boolean selectsOk() {
        try {
            jdbcTemplate.queryForList(
                "SELECT id, " + String.join(", ", ENTRY_COLUMNS.keySet()) + " FROM my_calendar_entry LIMIT 1");
            return true;
        } catch (Exception e) {
            log.error("[MyCalendar] my_calendar_entry 스키마가 코드와 맞지 않는다 — 캘린더 API 가 500 을 낸다: {}",
                e.getMessage(), e);
            return false;
        }
    }

    /**
     * 이 코드가 모르는 컬럼 중 "NOT NULL + 기본값 없음" 이 있으면 INSERT 가 통째로 실패한다
     * (STRICT_TRANS_TABLES: Field '...' doesn't have a default value). 남의 프로젝트가 만든
     * 컬럼일 수 있어 마음대로 고치지 않고, 어떤 컬럼 때문인지만 정확히 남긴다.
     */
    private boolean noBlockingLegacyColumns() {
        try {
            final List<String> blocking = new ArrayList<>(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'my_calendar_entry' "
                    + "  AND is_nullable = 'NO' AND column_default IS NULL "
                    + "  AND extra NOT LIKE '%auto_increment%'",
                String.class));
            blocking.removeAll(ENTRY_COLUMNS.keySet());
            blocking.remove("id");
            if (blocking.isEmpty()) {
                return true;
            }
            log.error("[MyCalendar] my_calendar_entry 에 이 코드가 모르는 필수 컬럼이 있다 {} — 일정 저장이 500 을"
                + " 낸다. 해당 컬럼에 DEFAULT 를 주거나 NULL 을 허용해야 한다.", blocking);
            return false;
        } catch (Exception e) {
            log.error("[MyCalendar] my_calendar_entry 컬럼 점검 실패: {}", e.getMessage(), e);
            return false;
        }
    }
}
