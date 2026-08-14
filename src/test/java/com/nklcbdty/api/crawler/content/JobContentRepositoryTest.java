package com.nklcbdty.api.crawler.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import com.nklcbdty.common.vo.Job_mst;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * {@code findNeedingContent} 의 JPQL 검증.
 *
 * <p>{@code @Query} 로 쓴 JPQL 은 앱 기동 시점에 파싱된다 — 틀리면 배포가 그대로 장애가 된다.
 * 운영 DB 없이 미리 걸러내려고 인메모리 DB 로 실제 실행해 본다.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:jobcontent;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
@Import(JobContentRepositoryTest.QuerydslTestConfig.class)
class JobContentRepositoryTest {

    /**
     * {@code @EnableJpaRepositories} 가 common jar 의 리포지토리까지 함께 올린다.
     * 그중 QueryDSL 을 쓰는 구현체가 JPAQueryFactory 를 요구하는데, JPA 슬라이스에는 그 빈이 없다.
     */
    @TestConfiguration
    static class QuerydslTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager em) {
            return new JPAQueryFactory(em);
        }
    }

    @Autowired
    private JobContentRepository repository;

    @Autowired
    private EntityManager em;

    private Job_mst job(String subJob, String link) {
        Job_mst job = new Job_mst();
        job.setCompanyCd("BAEMIN");
        job.setAnnoId("a" + System.nanoTime());
        job.setAnnoSubject("서버 개발자");
        job.setSubJobCdNm(subJob);
        job.setJobDetailLink(link);
        em.persist(job);
        return job;
    }

    private void content(Long jobId, LocalDateTime fetchedAt) {
        JobContent row = new JobContent(jobId, "BAEMIN");
        row.setFetchedAt(fetchedAt);
        em.persist(row);
    }

    @Test
    void 아직_수집_안_한_공고를_준다() {
        Job_mst target = job("Backend", "https://example.com/1");
        em.flush();

        List<Job_mst> result = repository.findNeedingContent(
            LocalDateTime.now().minusDays(14), PageRequest.of(0, 10));

        assertEquals(1, result.size());
        assertEquals(target.getId(), result.get(0).getId());
    }

    @Test
    void 최근에_수집한_공고는_빼고_오래된_건_다시_준다() {
        Job_mst fresh = job("Backend", "https://example.com/fresh");
        Job_mst stale = job("Backend", "https://example.com/stale");
        em.flush();
        content(fresh.getId(), LocalDateTime.now().minusDays(1));
        content(stale.getId(), LocalDateTime.now().minusDays(30));
        em.flush();

        List<Job_mst> result = repository.findNeedingContent(
            LocalDateTime.now().minusDays(14), PageRequest.of(0, 10));

        assertEquals(List.of(stale.getId()), result.stream().map(Job_mst::getId).toList());
    }

    @Test
    void 미분류_공고와_링크없는_공고는_대상이_아니다() {
        job(null, "https://example.com/unclassified");
        job("Backend", "");
        job("Backend", null);
        em.flush();

        assertTrue(repository.findNeedingContent(
            LocalDateTime.now().minusDays(14), PageRequest.of(0, 10)).isEmpty());
    }

    @Test
    void 한_번에_가져올_건수를_제한한다() {
        for (int i = 0; i < 5; i++) {
            job("Backend", "https://example.com/" + i);
        }
        em.flush();

        assertEquals(2, repository.findNeedingContent(
            LocalDateTime.now().minusDays(14), PageRequest.of(0, 2)).size());
    }
}
