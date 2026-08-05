package com.nklcbdty.api.board.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * spring.jpa.hibernate.ddl-auto=none 이므로 JPA 가 테이블을 만들지 않는다.
 * 앱 기동 시 게시판 테이블이 없으면 생성한다(JobDeleteRequestSchemaInitializer 와 같은 방식).
 *
 * 한 번만 시도하지 않고 재시도하는 이유:
 * hibernate.dialect 를 명시해 둔 덕에 이 앱은 DB 연결 없이도 기동한다. 그래서 컨테이너가
 * DB 보다 먼저 뜬 기동에서는 여기서 DDL 만 조용히 실패하고 앱은 정상으로 보였다.
 * 그 상태로 서비스되면 board_post 가 없어 /api/board/** 전체가 500 을 낸다.
 * (클라우드타입 무료 플랜이 새벽에 서비스를 내렸다 올리므로 매일 이 창이 열린다)
 *
 * DDL 이 끝나면 실제로 SELECT 가 되는지까지 확인한다 — CREATE 가 성공했는지와
 * 애플리케이션이 그 테이블을 읽을 수 있는지는 별개다(권한 등).
 */
@Slf4j
@Component
public class BoardSchemaInitializer implements ApplicationRunner {

    /**
     * DB 가 늦게 뜨는 경우를 감안한 재시도 횟수/간격. DB 가 끝까지 안 뜨면 기동이 45초 늘어난다.
     * 이 러너는 웹서버가 이미 요청을 받는 상태에서 돌기 때문에 그 사이 다른 API 는 정상 동작하고,
     * 예열 워크플로도 07:50 이라 여유가 있다. 새벽 재기동에서 DB 컨테이너가 같이 뜨는 시간을
     * 넘겨야 하므로 짧게 잡으면 의미가 없다.
     */
    static final int MAX_ATTEMPTS = 10;
    static final long RETRY_DELAY_MS = 5_000L;

    /** db/migration/V4__board_tables.sql 과 같은 내용이어야 한다. 한쪽만 바꾸지 말 것. */
    private static final String POST_DDL =
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

    private static final String COMMENT_DDL =
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

    private final JdbcTemplate jdbcTemplate;
    private final long retryDelayMs;

    public BoardSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, RETRY_DELAY_MS);
    }

    /** 테스트에서 대기 없이 재시도를 확인하기 위한 생성자. */
    BoardSchemaInitializer(JdbcTemplate jdbcTemplate, long retryDelayMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (prepareTables(attempt)) {
                log.info("[Board] board_post / board_comment 테이블 확인/생성 완료 (시도 {}회)", attempt);
                return;
            }
            if (attempt < MAX_ATTEMPTS && !sleepBeforeRetry()) {
                break;
            }
        }

        // 여기까지 왔으면 게시판은 못 쓰는 상태로 서비스된다. 조용히 넘어가면 안 된다.
        log.error("[Board] 게시판 테이블을 준비하지 못했습니다. /api/board/** 는 500 을 냅니다. "
            + "운영 DB 에 db/migration/V4__board_tables.sql 을 직접 적용하세요.");
    }

    /** DDL 실행 + 실제로 읽히는지 확인. 성공하면 true. */
    private boolean prepareTables(int attempt) {
        try {
            jdbcTemplate.execute(POST_DDL);
            jdbcTemplate.execute(COMMENT_DDL);
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM board_post", Integer.class);
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM board_comment", Integer.class);
            return true;
        } catch (Exception e) {
            log.warn("[Board] 게시판 테이블 준비 실패 ({}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
            return false;
        }
    }

    /** 재시도 대기. 인터럽트되면 더 기다리지 않는다. */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
