package com.nklcbdty.api.career;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.dto.CareerEntryRequest;
import com.nklcbdty.api.career.repository.CareerEntryRepository;
import com.nklcbdty.api.career.service.CareerEntryService;
import com.nklcbdty.api.career.vo.CareerEntry;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * 정렬 JPQL 과 값 풀기 검증.
 *
 * <p>{@code @Query} 의 {@code case when} 정렬은 앱 기동 시점에 파싱되므로 실제로 실행해 본다.
 * 기록은 로컬 스킬이 넣는 것이라 엔티티에 setter 가 없어 네이티브 INSERT 로 심는다
 * ({@code TroubleshootingNoteQueryTest} 와 같은 방식).
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:career;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
@Import(CareerEntryQueryTest.QuerydslTestConfig.class)
class CareerEntryQueryTest {

    /** common jar 의 QueryDSL 리포지토리가 이 빈을 요구한다. JPA 슬라이스에는 없어서 직접 넣는다 */
    @TestConfiguration
    static class QuerydslTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager em) {
            return new JPAQueryFactory(em);
        }
    }

    @Autowired
    private CareerEntryRepository repository;

    @Autowired
    private EntityManager em;

    private CareerEntryService service;

    @BeforeEach
    void setUp() {
        service = new CareerEntryService(repository);
    }

    private void entry(String slug, String type, String company, String startedOn, String endedOn,
                       String achievements) {
        em.createNativeQuery(
            "insert into career_entry "
          + "(slug, entry_type, company, title, started_on, ended_on, achievements, tech_stack, "
          + " created_at, updated_at) "
          + "values (?, ?, ?, '제목', ?, ?, ?, 'Java, Spring', current_timestamp, current_timestamp)")
            .setParameter(1, slug)
            .setParameter(2, type)
            .setParameter(3, company)
            .setParameter(4, startedOn == null ? null : java.sql.Date.valueOf(startedOn))
            .setParameter(5, endedOn == null ? null : java.sql.Date.valueOf(endedOn))
            .setParameter(6, achievements)
            .executeUpdate();
    }

    @Test
    void 진행중이_먼저_그다음_시작일_역순_날짜없는것은_맨뒤() {
        entry("old-company", "company", "A사", "2018-03-01", "2021-02-28", null);
        entry("cert", "certificate", null, null, null, null);
        entry("current-company", "company", "B사", "2021-03-02", null, null);
        // 진행 중인 것보다 시작일이 늦어도, 끝난 항목은 진행 중인 것 뒤로 간다
        entry("finished-recent", "project", "B사", "2024-01-01", "2024-06-30", null);
        entry("current-project", "project", "B사", "2023-01-01", null, null);
        em.flush();
        em.clear();

        List<String> slugs = service.findAll().stream().map(CareerEntryDto::getSlug).toList();

        assertEquals(List.of("current-project", "current-company", "finished-recent", "old-company", "cert"),
            slugs);
    }

    @Test
    void 성과는_줄마다_풀고_글머리표를_뗀다() {
        entry("p", "project", "B사", "2024-01-01", null, "- 응답시간 40% 단축\n\n• 장애 0건\r\n배포 자동화");
        em.flush();
        em.clear();

        CareerEntryDto dto = service.findAll().get(0);

        assertEquals(List.of("응답시간 40% 단축", "장애 0건", "배포 자동화"), dto.getAchievements());
        assertEquals(List.of("Java", "Spring"), dto.getTechStack());
        assertTrue(dto.getTags().isEmpty());
    }

    private static CareerEntryRequest request(String type, String title) {
        CareerEntryRequest r = new CareerEntryRequest();
        r.setEntryType(type);
        r.setTitle(title);
        return r;
    }

    @Test
    void 화면에서_추가하면_목록칸을_잇고_시각은_DB가_채운다() {
        CareerEntryRequest r = request(" Project ", "  정산 개편  ");
        r.setCompany("B사");
        r.setTeam("   ");
        r.setStartedOn(LocalDate.of(2023, 1, 1));
        r.setAchievements(List.of("배치 50% 단축", " ", "장애 0건"));
        r.setTechStack(List.of("Java", "Java", " Spring "));
        r.setTags(List.of("Spring Batch", "erp"));
        r.setReferenceLinks(List.of("PR: https://example.com/1"));

        CareerEntryDto created = service.create(r);
        em.flush();
        em.clear();

        CareerEntry saved = repository.findById(created.getId()).orElseThrow();
        assertEquals("project", saved.getEntryType());
        assertEquals("정산 개편", saved.getTitle());
        // 공백만 있는 칸은 비어 있는 것으로
        assertNull(saved.getTeam());
        assertEquals("배치 50% 단축\n장애 0건", saved.getAchievements());
        assertEquals("Java, Spring", saved.getTechStack());
        assertEquals("spring-batch, erp", saved.getTags());
        assertTrue(saved.getSlug().startsWith("project-"));
        // insertable=false 로 뺀 칼럼을 DB 기본값이 채웠는지
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void 수정해도_slug_는_그대로다() {
        entry("co-b", "company", "B사", "2021-03-01", null, null);
        em.flush();
        em.clear();
        Long id = repository.findAll().get(0).getId();

        CareerEntryRequest r = request("company", "백엔드 개발");
        r.setCompany("B사");
        r.setRole("주임연구원");
        service.update(id, r);
        em.flush();
        em.clear();

        CareerEntry saved = repository.findById(id).orElseThrow();
        assertEquals("co-b", saved.getSlug());
        assertEquals("주임연구원", saved.getRole());
        // 요청에 시작일이 없으면 지운다 — 화면이 칸 전체를 보내는 '통째 교체' 다
        assertNull(saved.getStartedOn());
    }

    @Test
    void 잘못된_입력은_막는다() {
        assertThrows(IllegalArgumentException.class, () -> service.create(request("", "제목")));
        assertThrows(IllegalArgumentException.class, () -> service.create(request("project", " ")));
        assertThrows(IllegalArgumentException.class, () -> service.create(request("회사", "제목")));

        CareerEntryRequest reversed = request("company", "제목");
        reversed.setStartedOn(LocalDate.of(2023, 5, 1));
        reversed.setEndedOn(LocalDate.of(2023, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> service.create(reversed));
    }

    @Test
    void 없는_항목은_수정도_삭제도_못한다() {
        assertThrows(NoSuchElementException.class, () -> service.update(999L, request("company", "x")));
        assertThrows(NoSuchElementException.class, () -> service.delete(999L));
    }

    @Test
    void 삭제한다() {
        CareerEntryDto created = service.create(request("certificate", "정보처리기사"));
        em.flush();

        service.delete(created.getId());
        em.flush();

        assertTrue(service.findAll().isEmpty());
    }
}
