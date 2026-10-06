package com.nklcbdty.api.career;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.repository.CareerEntryRepository;
import com.nklcbdty.api.career.service.CareerEntryService;
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
}
