package com.nklcbdty.api.log.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.nklcbdty.api.log.config.VisitorSchemaInitializer;
import com.nklcbdty.api.log.dto.VisitorSearchCondition;
import com.nklcbdty.api.log.entity.VisitorEntity;
import com.nklcbdty.api.log.geo.IpOriginClassifier;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/** 관리자 접속 로그 조회(QueryDSL)와 ip_origin 백필을 실제 쿼리로 돌려 본다. */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:visitor;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
@Import(VisitorRepositorySearchTest.Config.class)
class VisitorRepositorySearchTest {

    @TestConfiguration
    static class Config {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager em) {
            return new JPAQueryFactory(em);
        }
    }

    @Autowired
    private VisitorRepository visitorRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager em;

    private static Date at(int day, int hour) {
        return Date.from(LocalDateTime.of(2026, 11, day, hour, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    private void visit(String path, String ip, Date when) {
        visitorRepository.save(VisitorEntity.builder().path(path).insert_ip(ip).insert_dts(when).build());
    }

    /** 컬럼만 있고 ip_origin 이 비어 있는 옛 행을 넣고 백필을 돌린다 */
    private void seed() {
        // 초기화기의 ALTER TABLE 이 암묵적으로 커밋해서 앞 테스트의 행이 롤백되지 않고 남는다
        jdbcTemplate.update("DELETE FROM visitor");
        visit("/api/list?company=ALL", "121.137.86.218", at(15, 10)); // 국내
        visit("/api/list?company=TOSS", "66.249.90.130", at(16, 10)); // 구글봇
        visit("/api/list?company=ALL", "지용_사무실", at(16, 11));     // 팀원
        visit("/api/detail", "3.41.210.138", at(17, 9));             // 서울 AWS 봇
        visit("/api/list?company=NAVER", "119.197.67.240", at(17, 12)); // 국내
        em.flush();
        new VisitorSchemaInitializer(jdbcTemplate, new IpOriginClassifier()).run(null);
        em.clear();
    }

    @Test
    void 백필이_출처를_채운다() {
        seed();
        final List<String> origins = jdbcTemplate.queryForList(
            "SELECT ip_origin FROM visitor ORDER BY id", String.class);
        assertThat(origins).containsExactly("DOMESTIC", "FOREIGN", "INTERNAL", "FOREIGN", "DOMESTIC");
    }

    @Test
    void 국내만_최신순() {
        seed();
        final Page<VisitorEntity> page = visitorRepository.search(
            VisitorSearchCondition.builder().domesticOnly(true).build(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(VisitorEntity::getInsert_ip)
            .containsExactly("119.197.67.240", "지용_사무실", "121.137.86.218");
    }

    @Test
    void 국내만_팀원_제외_기간_검색어() {
        seed();
        final Page<VisitorEntity> page = visitorRepository.search(
            VisitorSearchCondition.builder()
                .domesticOnly(true).excludeInternal(true)
                .keyword("company").from(at(16, 0)).to(at(18, 0))
                .build(),
            PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(VisitorEntity::getInsert_ip).containsExactly("119.197.67.240");
    }

    @Test
    void 페이지를_나눈다() {
        seed();
        final Page<VisitorEntity> page = visitorRepository.search(
            VisitorSearchCondition.builder().build(), PageRequest.of(1, 2));
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(VisitorEntity::getInsert_ip)
            .containsExactly("지용_사무실", "66.249.90.130");
    }

    @Test
    void 출처별_건수는_출처_조건을_무시한다() {
        seed();
        final Map<String, Long> counts = visitorRepository.countByOrigin(
            VisitorSearchCondition.builder().domesticOnly(true).from(at(16, 0)).build());
        assertThat(counts).containsEntry("DOMESTIC", 1L)
            .containsEntry("FOREIGN", 2L)
            .containsEntry("INTERNAL", 1L)
            .containsEntry("UNKNOWN", 0L);
    }
}
