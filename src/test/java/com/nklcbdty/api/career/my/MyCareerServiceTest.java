package com.nklcbdty.api.career.my;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.nklcbdty.api.career.my.repository.MyCareerEntryRepository;
import com.nklcbdty.api.career.my.service.MyCareerService;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * 사용자 구분과 정렬 JPQL 을 실제 DB(H2)로 검증한다. 남의 항목이 보이거나 고쳐지는 것이 가장 큰 사고다.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:mycareer;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
@Import(MyCareerServiceTest.QuerydslTestConfig.class)
class MyCareerServiceTest {

    /** common jar 의 QueryDSL 리포지토리가 이 빈을 요구한다. JPA 슬라이스에는 없어서 직접 넣는다 */
    @TestConfiguration
    static class QuerydslTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager em) {
            return new JPAQueryFactory(em);
        }
    }

    private static final String ME = "local@1";
    private static final String OTHER = "kakao@2";

    @Autowired
    private MyCareerEntryRepository repository;

    private MyCareerService service;

    @BeforeEach
    void setUp() {
        service = new MyCareerService(repository);
    }

    private static CareerEntryRequest request(String type, String title, String startedOn, String endedOn) {
        CareerEntryRequest r = new CareerEntryRequest();
        r.setEntryType(type);
        r.setTitle(title);
        r.setStartedOn(startedOn == null ? null : LocalDate.parse(startedOn));
        r.setEndedOn(endedOn == null ? null : LocalDate.parse(endedOn));
        return r;
    }

    @Test
    void 내_것만_최근_것부터_보인다() {
        service.create(ME, request("company", "옛 회사", "2018-01-01", "2020-12-01"));
        service.create(ME, request("certificate", "정보처리기사", null, null));
        service.create(ME, request("company", "지금 회사", "2021-01-01", null));
        service.create(OTHER, request("company", "남의 회사", "2022-01-01", null));

        List<String> titles = service.findAll(ME).stream().map(CareerEntryDto::getTitle).toList();

        assertEquals(List.of("지금 회사", "옛 회사", "정보처리기사"), titles);
    }

    @Test
    void 남의_항목은_고치지도_지우지도_못한다() {
        CareerEntryDto others = service.create(OTHER, request("company", "남의 회사", "2022-01-01", null));

        assertThrows(NoSuchElementException.class,
            () -> service.update(ME, others.getId(), request("company", "가로채기", null, null)));
        assertThrows(NoSuchElementException.class, () -> service.delete(ME, others.getId()));

        assertEquals("남의 회사", service.findAll(OTHER).get(0).getTitle());
    }

    @Test
    void 수정하면_값이_바뀌고_목록_칸은_풀려서_나온다() {
        CareerEntryDto created = service.create(ME, request("project", "검색 개선", "2023-03-01", null));

        CareerEntryRequest edit = request("project", "  검색 개선 2차  ", "2023-03-01", "2023-09-01");
        edit.setCompany("  ");
        edit.setTechStack(List.of("Java", " Elasticsearch ", "Java"));
        edit.setAchievements(List.of("응답 시간 40% 단축", ""));
        CareerEntryDto updated = service.update(ME, created.getId(), edit);

        assertEquals("검색 개선 2차", updated.getTitle());
        assertNull(updated.getCompany());
        assertEquals(List.of("Java", "Elasticsearch"), updated.getTechStack());
        assertEquals(List.of("응답 시간 40% 단축"), updated.getAchievements());
        assertEquals(LocalDate.parse("2023-09-01"), updated.getEndedOn());
    }

    @Test
    void 잘못된_입력은_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> service.create(ME, request("company", " ", null, null)));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(ME, request("company", "회사", "2023-01-01", "2022-01-01")));
        assertThrows(IllegalArgumentException.class, () -> service.create(ME, request("회사", "회사", null, null)));
        assertTrue(service.findAll(ME).isEmpty());
    }

    @Test
    void 삭제하면_사라진다() {
        CareerEntryDto created = service.create(ME, request("etc", "수상", null, null));
        service.delete(ME, created.getId());
        assertTrue(service.findAll(ME).isEmpty());
    }
}
