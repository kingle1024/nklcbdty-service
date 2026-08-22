package com.nklcbdty.api.mycalendar.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 스키마 초기화를 진짜 DB(H2, MySQL 모드) 에 대고 돌린다.
 *
 * <p>이 코드가 틀리면 앱은 뜨는데 캘린더 API 만 500 이 나거나, 최악의 경우 기동이 멈춘다.
 * 눈으로 읽어서는 알 수 없는 부분이라(특히 "이미 NOT NULL 인 테이블" 경로) 실제로 실행해 본다.
 * H2 는 MariaDB 가 아니므로 DDL 문법이 통하는지까지만 보증한다 — 락 동작은 흉내내지 못한다.</p>
 */
class MyCalendarSchemaInitializerTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private MyCalendarSchemaInitializer initializer;

    @BeforeEach
    void setUp() {
        // 테스트마다 새 인메모리 DB. MySQL 모드라야 MODIFY COLUMN 같은 문법이 통한다.
        dataSource = new SingleConnectionDataSource(
            "jdbc:h2:mem:mycal-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "", true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        initializer = new MyCalendarSchemaInitializer(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    private String nullabilityOf(String column) {
        final List<String> value = jdbcTemplate.queryForList(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE LOWER(table_name) = 'my_calendar_entry' AND LOWER(column_name) = ?",
            String.class, column);
        return value.isEmpty() ? "(컬럼 없음)" : value.get(0);
    }

    @Test
    @DisplayName("빈 DB 에서: 테이블을 만들고 상시채용(날짜 없는 행)이 저장된다")
    void createsTableThatAcceptsOngoingRows() {
        initializer.run(null);

        assertThat(nullabilityOf("apply_date")).isEqualToIgnoringCase("YES");
        assertThatCode(() -> jdbcTemplate.update(
            "INSERT INTO my_calendar_entry (user_id, apply_date, company_name, completed) "
                + "VALUES ('local@me', NULL, '카카오', 0)"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("상시채용 이전에 만들어진 테이블(apply_date NOT NULL)의 NOT NULL 을 풀어 준다")
    void relaxesNotNullOnAnExistingTable() {
        // 이 기능 전의 스키마를 그대로 만든다 — completed 컬럼도 없다.
        jdbcTemplate.execute(
            "CREATE TABLE my_calendar_entry ("
                + "  id BIGINT NOT NULL AUTO_INCREMENT,"
                + "  user_id VARCHAR(255) NOT NULL,"
                + "  apply_date DATE NOT NULL,"
                + "  company_name VARCHAR(100) NOT NULL,"
                + "  url VARCHAR(1000) NULL,"
                + "  memo VARCHAR(2000) NULL,"
                + "  insert_dts DATETIME NULL,"
                + "  update_dts DATETIME NULL,"
                + "  PRIMARY KEY (id))");
        jdbcTemplate.update("INSERT INTO my_calendar_entry (user_id, apply_date, company_name) "
            + "VALUES ('local@me', '2026-08-25', '네이버')");
        assertThat(nullabilityOf("apply_date")).isEqualToIgnoringCase("NO");

        initializer.run(null);

        assertThat(nullabilityOf("apply_date")).isEqualToIgnoringCase("YES");
        assertThat(nullabilityOf("completed")).isEqualToIgnoringCase("NO");
        // 이미 있던 행이 살아 있어야 한다. 마이그레이션이 데이터를 날리면 안 된다.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT company_name FROM my_calendar_entry WHERE id = 1", String.class)).isEqualTo("네이버");
        // 그리고 이제 상시채용이 들어간다.
        assertThatCode(() -> jdbcTemplate.update(
            "INSERT INTO my_calendar_entry (user_id, apply_date, company_name, completed) "
                + "VALUES ('local@me', NULL, '쿠팡', 0)"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("여러 번 기동해도 안전하다 — 두 번째 실행은 아무것도 바꾸지 않는다")
    void isSafeToRunTwice() {
        initializer.run(null);

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
        assertThat(nullabilityOf("apply_date")).isEqualToIgnoringCase("YES");
    }

    /**
     * 기동을 멈추지 않는다는 것이 이 클래스의 가장 중요한 성질이다. DataSource 가 죽어 있어도
     * 예외가 밖으로 나가면 ApplicationRunner 가 기동을 실패시킨다.
     */
    @Test
    @DisplayName("DB 가 죽어 있어도 예외를 던지지 않는다 — 기동을 막으면 안 된다")
    void neverThrowsEvenWhenTheDatabaseIsUnreachable() {
        final DataSource broken = new SingleConnectionDataSource(
            "jdbc:h2:mem:nope;IFEXISTS=TRUE", "sa", "", true);
        final MyCalendarSchemaInitializer onBrokenDb =
            new MyCalendarSchemaInitializer(new JdbcTemplate(broken));

        assertThatCode(() -> onBrokenDb.run(null)).doesNotThrowAnyException();
    }
}
