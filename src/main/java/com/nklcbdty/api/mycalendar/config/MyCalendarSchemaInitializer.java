package com.nklcbdty.api.mycalendar.config;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
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

    /** 락을 기다리다 기동을 멈추지 않도록 하는 상한. 짧게 잡고 다음 기동에 다시 시도한다. */
    private static final int LOCK_WAIT_SECONDS = 10;
    private static final int QUERY_TIMEOUT_SECONDS = 20;

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
            alterWithBoundedWait(ddl);
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
            alterWithBoundedWait(
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
            if (definitelyNullable(column)) {
                return;
            }
            alterWithBoundedWait("ALTER TABLE my_calendar_entry MODIFY COLUMN " + column + " " + definition);
            log.info("[MyCalendar] my_calendar_entry.{} 를 NULL 허용으로 바꿨다 — 상시채용 저장 가능", column);
        } catch (Exception e) {
            // 실패하면 상시채용 저장이 500 이 난다. 원인을 바로 알 수 있게 남긴다.
            log.error("[MyCalendar] my_calendar_entry.{} NOT NULL 해제 실패 — 상시채용(날짜 없는 일정) 저장이"
                + " 실패한다: {}", column, e.getMessage(), e);
        }
    }

    /**
     * DDL 을 <b>반드시 시간 안에 끝나게</b> 실행한다.
     *
     * <p>이 DB(travel 스키마)는 다른 프로젝트와 함께 쓴다. 남의 긴 트랜잭션이 이 테이블을 잡고 있으면
     * ALTER 는 메타데이터 락을 기다리며 <b>예외도 없이 멈춘다</b>. 이 코드는 ApplicationRunner
     * 안에서 돌기 때문에, 그대로 기다리면 기동이 끝나지 않고 컨테이너가 안 뜬다 —
     * "재배포는 성공인데 앱이 안 뜬다" 가 이 프로젝트에서 이미 여러 번 있었다.</p>
     *
     * <p>그래서 두 겹으로 막는다. {@code lock_wait_timeout} 은 락 대기를, JDBC
     * {@code queryTimeout} 은 그마저 안 통할 때를 자른다. 포기해도 다음 기동에서 다시 시도하므로
     * 잃는 것이 없다(그때까지 상시채용 저장만 안 된다).</p>
     *
     * <p>두 문장이 <b>같은 커넥션</b>에서 돌아야 해서 ConnectionCallback 을 쓴다.
     * {@code jdbcTemplate.execute(String)} 를 두 번 부르면 풀에서 다른 커넥션을 받아
     * {@code SET SESSION} 이 ALTER 에 적용되지 않는다.</p>
     */
    private void alterWithBoundedWait(String ddl) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                // MySQL 5.5+/MariaDB 에만 있다. 없는 DB 라도 ALTER 는 시도해야 하므로 여기서 삼킨다.
                try {
                    statement.execute("SET SESSION lock_wait_timeout = " + LOCK_WAIT_SECONDS);
                } catch (SQLException e) {
                    log.debug("[MyCalendar] lock_wait_timeout 설정 실패(무시): {}", e.getMessage());
                }
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.execute(ddl);
            }
            return null;
        });
    }

    /**
     * 컬럼이 NULL 을 허용한다고 <b>확인된</b> 경우에만 true.
     *
     * <p>모르겠으면 false 다. "모르겠다" 를 "허용한다" 로 보면 ALTER 를 건너뛰는데, 실제로는
     * NOT NULL 이었을 때 상시채용 저장이 500 이 나면서 <b>로그에 아무것도 남지 않는다</b>.
     * ALTER 는 같은 정의로 여러 번 걸어도 결과가 같으니, 확신이 없으면 그냥 걸어 보는 쪽이 안전하다.</p>
     *
     * <p>{@code table_schema = DATABASE()} 로 지금 쓰는 스키마만 본다 — 이 DB 는 여러 프로젝트가
     * 공유하므로 같은 이름의 테이블이 다른 스키마에 있을 수 있다.</p>
     */
    private boolean definitelyNullable(String column) {
        try {
            final List<String> nullable = jdbcTemplate.queryForList(
                "SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND LOWER(table_name) = 'my_calendar_entry' "
                    + "  AND LOWER(column_name) = ?",
                String.class, column);
            return !nullable.isEmpty() && "YES".equalsIgnoreCase(nullable.get(0));
        } catch (Exception e) {
            // 조회 자체가 안 되면 알 수 없다. ALTER 를 걸어 보고 그 결과로 판단하게 둔다.
            log.debug("[MyCalendar] {} 컬럼 nullability 조회 실패 — ALTER 를 그대로 시도한다: {}",
                column, e.getMessage());
            return false;
        }
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
