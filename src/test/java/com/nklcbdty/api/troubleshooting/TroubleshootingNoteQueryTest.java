package com.nklcbdty.api.troubleshooting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteDetailDto;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteSummaryDto;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingPageResponse;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingTextParts.ReferenceLink;
import com.nklcbdty.api.troubleshooting.repository.TroubleshootingNoteRepository;
import com.nklcbdty.api.troubleshooting.service.TroubleshootingNoteService;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * 목록 조회 JPQL 과 필터 조합 검증.
 *
 * <p>{@code @Query} 로 쓴 JPQL 은 앱 기동 시점에 파싱된다 — 틀리면 배포가 그대로 장애가 된다.
 * 특히 이 조회는 (1) 안 쓰는 조건을 빈 문자열로 넘기는 방식과 (2) 쉼표로 이어붙은 태그
 * 칼럼을 {@code %,tag,%} 로 맞추는 방식, 둘 다 SQL 로 번역돼야 성립하므로 실제로 실행해 본다.
 *
 * <p>기록은 로컬 스킬이 넣는 것이라 엔티티에 setter 가 없다. 그래서 테스트 데이터는
 * 네이티브 INSERT 로 심는다.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:tsnote;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration"
})
@Import(TroubleshootingNoteQueryTest.QuerydslTestConfig.class)
class TroubleshootingNoteQueryTest {

    /** common jar 의 QueryDSL 리포지토리가 이 빈을 요구한다. JPA 슬라이스에는 없어서 직접 넣는다 */
    @TestConfiguration
    static class QuerydslTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager em) {
            return new JPAQueryFactory(em);
        }
    }

    /** 스킬이 저장하는 모양 그대로 — 주소가 있는 줄과 없는 줄이 섞여 있다 */
    private static final String LINKS = "PR: https://example.com/pr/1\n머지 커밋: abc1234";

    @Autowired
    private TroubleshootingNoteRepository repository;

    @Autowired
    private EntityManager em;

    private TroubleshootingNoteService service;

    @BeforeEach
    void setUp() {
        service = new TroubleshootingNoteService(repository);
    }

    private void note(String slug, String occurredOn, String project, String title,
                      String severity, String tags, String rootCause) {
        em.createNativeQuery(
            "insert into troubleshooting_note "
          + "(slug, occurred_on, project, component, title, severity, error_code, symptom, "
          + " root_cause, resolution, verification, prevention, lesson, tech_stack, tags, "
          + " reference_links, created_at, updated_at) "
          + "values (?, ?, ?, '배치', ?, ?, 'ORA-20002', '증상 원문', ?, '해결', '검증', "
          + "        '예방', '교훈 한 줄', 'Java, Spring', ?, ?, "
          + "        current_timestamp, current_timestamp)")
            .setParameter(1, slug)
            .setParameter(2, java.sql.Date.valueOf(occurredOn))
            .setParameter(3, project)
            .setParameter(4, title)
            .setParameter(5, severity)
            .setParameter(6, rootCause)
            .setParameter(7, tags)
            .setParameter(8, LINKS)
            .executeUpdate();
    }

    private void seed() {
        note("a-batch-halt", "2026-08-20", "dart-tracker", "배치가 첫 날짜에서 멈췄다",
            "high", "batch,pagination,race-condition", "페이지네이션이 스냅샷이 아니었다");
        // 같은 날 두 건 — 정렬이 id 로 못박히는지 보려고 일부러 날짜를 겹친다
        note("b-same-day", "2026-08-20", "dart-tracker", "같은 날 두 번째 기록",
            "medium", "batch-window, silent-failure", "고정 창이 늦은 거래일을 버렸다");
        note("c-ssh-refused", "2026-08-18", "OmniEsol ERP10", "백업 배치가 조용히 실패",
            "critical", "ssh, jsch", "표준포트 변경과 설정 불일치");
        em.flush();
        em.clear();
    }

    @Test
    void 조건이_없으면_발생일_최신순으로_준다() {
        seed();

        TroubleshootingPageResponse page = service.list(null, null, null, null, 0, 20);

        assertEquals(3, page.getTotalElements());
        assertEquals(3, page.getTotalNotes());
        // 같은 날짜(08-20) 두 건은 나중에 넣은 것(id 가 큰 것)이 먼저 온다
        assertEquals(List.of("b-same-day", "a-batch-halt", "c-ssh-refused"), slugs(page));
    }

    @Test
    void 검색어는_본문과_태그까지_훑는다() {
        seed();

        // 제목에도 태그에도 없고 root_cause 본문에만 있는 말
        assertEquals(List.of("a-batch-halt"), slugs(service.list("스냅샷", null, null, null, 0, 20)));
        // 태그에만 있는 말
        assertEquals(List.of("c-ssh-refused"), slugs(service.list("jsch", null, null, null, 0, 20)));
    }

    @Test
    void 태그_필터는_앞부분만_같은_태그를_잡지_않는다() {
        seed();

        // 'batch' 는 batch 를 가진 기록만. 'batch-window' 는 걸리지 않아야 한다
        assertEquals(List.of("a-batch-halt"), slugs(service.list(null, null, null, "batch", 0, 20)));
        // 쉼표 뒤에 공백을 두고 저장한 태그도 잡힌다
        assertEquals(List.of("b-same-day"), slugs(service.list(null, null, null, "silent-failure", 0, 20)));
        assertTrue(slugs(service.list(null, null, null, "없는태그", 0, 20)).isEmpty());
    }

    @Test
    void 프로젝트와_심각도로_좁힌다() {
        seed();

        assertEquals(List.of("b-same-day", "a-batch-halt"),
            slugs(service.list(null, "dart-tracker", null, null, 0, 20)));
        // 대소문자를 섞어 넣어도 맞는다
        assertEquals(List.of("c-ssh-refused"), slugs(service.list(null, null, "CRITICAL", null, 0, 20)));
        // 조건을 겹치면 교집합
        assertEquals(List.of("a-batch-halt"), slugs(service.list(null, "dart-tracker", "high", null, 0, 20)));
    }

    @Test
    void 필터_후보와_집계는_검색과_무관하게_전체_기준이다() {
        seed();

        // 한 건만 남는 조건으로 좁혀도 선택지가 사라지면 다른 조건으로 갈아탈 수 없다
        TroubleshootingPageResponse page = service.list(null, null, "critical", null, 0, 20);

        assertEquals(1, page.getTotalElements());
        assertEquals(3, page.getTotalNotes());
        assertEquals(List.of("dart-tracker", "OmniEsol ERP10"), page.getProjects());
        assertEquals(List.of("critical", "high", "medium"),
            List.copyOf(page.getSeverityCounts().keySet()));
        assertTrue(page.getTags().contains("batch"));
        assertTrue(page.getTags().contains("batch-window"));
    }

    @Test
    void 상세는_이어붙은_값을_풀어서_준다() {
        seed();

        TroubleshootingNoteDetailDto detail = service.findBySlug("a-batch-halt");

        assertEquals(List.of("batch", "pagination", "race-condition"), detail.getTags());
        assertEquals(List.of("Java", "Spring"), detail.getTechStack());
        // 주소가 있는 줄은 라벨+주소로, 없는 줄은 라벨만 남고 주소가 비어야 한다
        assertEquals(List.of("PR", "머지 커밋: abc1234"),
            detail.getReferenceLinks().stream().map(ReferenceLink::label).toList());
        assertEquals(List.of("https://example.com/pr/1"),
            detail.getReferenceLinks().stream().map(ReferenceLink::url).filter(u -> u != null).toList());
        assertNull(detail.getReferenceLinks().get(1).url());
    }

    @Test
    void 없는_slug_는_null_이다() {
        seed();

        assertNull(service.findBySlug("있지-않은-기록"));
    }

    @Test
    void 페이지_크기에는_상한이_있다() {
        seed();

        assertEquals(100, service.list(null, null, null, null, 0, 5000).getPageSize());
        assertEquals(20, service.list(null, null, null, null, 0, 0).getPageSize());
    }

    private static List<String> slugs(TroubleshootingPageResponse page) {
        return page.getRows().stream().map(TroubleshootingNoteSummaryDto::getSlug).toList();
    }
}
