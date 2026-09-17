package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code findStale} / {@code findEmbeddingsRaw} 의 JPQL 검증.
 *
 * <p>{@code @Query} 로 쓴 JPQL 은 앱 기동 시점에 파싱된다 — 틀리면 배포가 그대로 장애가 된다.
 * 운영 DB 없이 미리 걸러내려고 인메모리 DB 로 실제 실행해 본다.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:jobembedding;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
@Import(JobEmbeddingRepositoryTest.QuerydslTestConfig.class)
class JobEmbeddingRepositoryTest {

    private static final String CURRENT = "text-embedding-3-small-512";
    private static final String OLD = "paraphrase-multilingual-MiniLM-L12-v2";

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
    private JobEmbeddingRepository repository;

    @Autowired
    private EntityManager em;

    private Job_mst job(byte[] embedding, String version) {
        Job_mst job = new Job_mst();
        job.setCompanyCd("BAEMIN");
        job.setAnnoId("a" + System.nanoTime());
        job.setAnnoSubject("서버 개발자");
        job.setEmbedding(embedding);
        job.setEmbeddingVersion(version);
        em.persist(job);
        return job;
    }

    private static byte[] vec(float... v) {
        return Vectors.toBytes(v);
    }

    @Test
    @DisplayName("임베딩이 없는 공고는 인덱싱 대상")
    void 미인덱스_대상() {
        Job_mst target = job(null, null);
        em.flush();

        List<Job_mst> result = repository.findStale(CURRENT, PageRequest.of(0, 10));

        assertEquals(List.of(target.getId()), result.stream().map(Job_mst::getId).toList());
    }

    @Test
    @DisplayName("버전이 다른 공고도 인덱싱 대상 — 공급자/차원이 바뀌면 다시 임베딩해야 한다")
    void 버전_다르면_대상() {
        Job_mst stale = job(vec(0.1f, 0.2f), OLD);
        Job_mst versionless = job(vec(0.3f), null);
        job(vec(0.4f, 0.5f), CURRENT); // 최신 버전 → 대상 아님
        em.flush();

        List<Long> ids = repository.findStale(CURRENT, PageRequest.of(0, 10))
                .stream().map(Job_mst::getId).toList();

        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of(stale.getId(), versionless.getId())));
    }

    @Test
    @DisplayName("한 번에 가져올 건수를 제한한다")
    void 페이지_제한() {
        for (int i = 0; i < 5; i++) job(null, null);
        em.flush();

        assertEquals(2, repository.findStale(CURRENT, PageRequest.of(0, 2)).size());
    }

    @Test
    @DisplayName("워밍업은 현재 버전 벡터만 싣는다")
    void 워밍업은_현재_버전만() {
        Job_mst current = job(vec(0.4f, 0.5f), CURRENT);
        job(vec(0.1f, 0.2f), OLD);
        job(null, null);
        em.flush();

        List<Object[]> rows = repository.findEmbeddingsRaw(CURRENT);

        assertEquals(1, rows.size());
        assertEquals(current.getId(), rows.get(0)[0]);
        assertEquals(2, Vectors.fromBytes((byte[]) rows.get(0)[1]).length);
    }
}
