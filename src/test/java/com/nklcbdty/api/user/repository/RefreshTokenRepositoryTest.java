package com.nklcbdty.api.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.nklcbdty.api.user.vo.RefreshTokenVo;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * 파생 쿼리(IsRevokedFalse 등)는 부팅 시점에 파싱되므로,
 * 여기서 깨지면 배포 직후 애플리케이션이 아예 뜨지 않는다. H2 로 실제 파싱·동작을 고정한다.
 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // 본 설정은 MariaDBDialect 로 고정돼 있어 H2 가 DDL 을 못 알아듣는다
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
@Import(RefreshTokenRepositoryTest.QuerydslTestConfig.class)
class RefreshTokenRepositoryTest {

    /** JPA 슬라이스에는 QuerydslConfig 가 없어서 다른 repository 구현체가 못 뜬다. 여기서 보충한다. */
    @TestConfiguration
    static class QuerydslTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @Autowired
    private RefreshTokenRepository repository;

    private RefreshTokenVo save(String userId, String hash, boolean revoked) {
        return repository.save(RefreshTokenVo.builder()
            .userId(userId)
            .token(hash)
            .issuedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(30))
            .isRevoked(revoked)
            .build());
    }

    @Test
    @DisplayName("userId+토큰해시로 최신 행을 찾는다 — 무효화된 행도 검증용으로는 조회된다")
    void findByUserIdAndToken() {
        save("local@1", "hash-a", true);
        save("local@1", "hash-a", false);
        save("local@2", "hash-b", false);

        Optional<RefreshTokenVo> found = repository.findFirstByUserIdAndTokenOrderByIdDesc("local@1", "hash-a");
        assertThat(found).isPresent();
        assertThat(found.get().isRevoked()).isFalse(); // 최신(id 큰) 행

        assertThat(repository.findFirstByUserIdAndTokenOrderByIdDesc("local@1", "hash-b")).isEmpty();
    }

    @Test
    @DisplayName("회전용 조회는 무효화되지 않은 행만 찾는다")
    void findNotRevoked() {
        save("local@1", "hash-a", true);
        assertThat(repository.findFirstByUserIdAndTokenAndIsRevokedFalseOrderByIdDesc("local@1", "hash-a")).isEmpty();

        save("local@1", "hash-a", false);
        assertThat(repository.findFirstByUserIdAndTokenAndIsRevokedFalseOrderByIdDesc("local@1", "hash-a")).isPresent();
    }
}
