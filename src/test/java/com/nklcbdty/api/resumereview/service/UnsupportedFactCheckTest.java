package com.nklcbdty.api.resumereview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.Change;
import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.ChangeType;

class UnsupportedFactCheckTest {

    private static final String RESUME = """
            경력: 백엔드 개발자 3년
            - 주문 API 서버 개발 (Java, Spring Boot, JPA)
            - MySQL 쿼리 튜닝으로 조회 속도 개선
            - 장애 대응 당번 참여
            기술: Java, Spring, MySQL, Redis""";
    private static final String POSTING =
            "자격요건: Java/Spring 기반 서버 개발 경험 3년 이상. 우대: Kafka 등 메시지 큐, 대용량 트래픽, MSA 경험";

    @Test
    @DisplayName("운영에서 나온 사례 — 공고 우대사항(MSA)을 이력서에 끼워 넣으면 잡는다")
    void 공고_키워드_끼워넣기() {
        assertThat(UnsupportedFactCheck.unsupported(
                "Java/Spring Boot 기반 주문 API 서버 개발 및 MSA 환경 구축 참여", RESUME, POSTING))
                .containsExactly("MSA");
    }

    @Test
    @DisplayName("원문에 있는 기술을 다시 쓰거나 한글 표현만 바꾸면 통과한다")
    void 표현_바꾸기는_통과() {
        assertThat(UnsupportedFactCheck.unsupported(
                "MySQL 쿼리 튜닝을 통한 조회 속도 개선", RESUME, POSTING)).isEmpty();
        assertThat(UnsupportedFactCheck.unsupported(
                "운영 장애 대응 당번 참여", RESUME, POSTING)).isEmpty();
    }

    @Test
    @DisplayName("직접 입력 자리표시 안의 숫자·영문은 보지 않는다")
    void 자리표시는_통과() {
        assertThat(UnsupportedFactCheck.unsupported(
                "MySQL 쿼리 튜닝으로 조회 속도 개선 ([직접 입력: 개선율 %, 예: 40% / TPS])", RESUME, POSTING)).isEmpty();
    }

    @Test
    @DisplayName("원문에 없는 수치는 지어낸 성과로 본다")
    void 지어낸_수치() {
        assertThat(UnsupportedFactCheck.unsupported(
                "MySQL 쿼리 튜닝으로 조회 속도 40% 개선", RESUME, POSTING)).containsExactly("40");
    }

    @Test
    @DisplayName("영문 기술명은 낱말 단위로 본다 — Javascript 가 있다고 Java 가 정당화되지 않는다")
    void 낱말_경계() {
        assertThat(UnsupportedFactCheck.unsupported("Java 서버 개발", "Javascript 프론트 개발", ""))
                .containsExactly("Java");
    }

    @Test
    @DisplayName("근거 없는 변경은 적용하지 않고 이유에 무엇이 문제인지 붙인다")
    void 적용_막기() {
        ChangeApplier.Result r = ChangeApplier.apply(RESUME, List.of(
                new ChangeApplier.Proposed(ChangeType.REPLACE, "경력", "주문 API 서버 개발 (Java, Spring Boot, JPA)",
                        "Java/Spring Boot 기반 주문 API 서버 개발 및 MSA 환경 구축 참여", "MSA 우대사항 반영"),
                new ChangeApplier.Proposed(ChangeType.REPLACE, "경력", "장애 대응 당번 참여",
                        "운영 장애 대응 당번 참여 ([직접 입력: 대응한 장애 유형])", "구체화")),
                RESUME, POSTING);

        assertThat(r.changes()).extracting(Change::applied).containsExactly(false, true);
        assertThat(r.changes().get(0).reason()).contains("MSA").contains("자동으로 반영하지 않았습니다");
        assertThat(r.revisedText()).doesNotContain("MSA").contains("운영 장애 대응 당번 참여");
    }
}
