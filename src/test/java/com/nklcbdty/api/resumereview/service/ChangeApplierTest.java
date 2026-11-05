package com.nklcbdty.api.resumereview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.Change;
import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.ChangeType;

class ChangeApplierTest {

    private static final String RESUME = """
            경력
            ○○커머스 백엔드 개발
            - 주문 API 개발
            - 배치 작업 개선
            기술: Java, Spring""";

    @Test
    @DisplayName("REPLACE 는 원문의 그 부분만 바꾸고 나머지는 그대로 둔다")
    void replace() {
        ChangeApplier.Result r = ChangeApplier.apply(RESUME, List.of(
                new ChangeApplier.Proposed(ChangeType.REPLACE, "경력", "주문 API 개발",
                        "Spring Boot 로 주문 API 설계·개발", "공고의 Spring Boot 요구")));

        assertThat(r.revisedText()).contains("- Spring Boot 로 주문 API 설계·개발\n- 배치 작업 개선");
        assertThat(r.changes()).extracting(Change::applied).containsExactly(true);
    }

    @Test
    @DisplayName("INSERT 는 기준 문장이 있는 줄 바로 뒤에 새 줄로 넣는다")
    void insertAfterLine() {
        ChangeApplier.Result r = ChangeApplier.apply(RESUME, List.of(
                new ChangeApplier.Proposed(ChangeType.INSERT, "경력", "배치 작업",
                        "- [직접 입력: 처리 시간 단축 %]", "성과 수치")));

        assertThat(r.revisedText()).contains("- 배치 작업 개선\n- [직접 입력: 처리 시간 단축 %]\n기술");
    }

    @Test
    @DisplayName("INSERT 의 기준 문장이 비면 문서 끝에 붙인다")
    void insertAtEnd() {
        ChangeApplier.Result r = ChangeApplier.apply(RESUME, List.of(
                new ChangeApplier.Proposed(ChangeType.INSERT, "", "", "자격증: 정보처리기사", "")));

        assertThat(r.revisedText()).endsWith("기술: Java, Spring\n자격증: 정보처리기사");
    }

    @Test
    @DisplayName("공백·줄바꿈만 다르면 느슨하게 찾아 적용한다 (PDF 줄바꿈)")
    void looseWhitespace() {
        String pdf = "주문 API\n개발 담당";
        ChangeApplier.Result r = ChangeApplier.apply(pdf, List.of(
                new ChangeApplier.Proposed(ChangeType.REPLACE, "", "주문 API 개발", "주문 API 설계", "")));

        assertThat(r.revisedText()).isEqualTo("주문 API 설계 담당");
        assertThat(r.changes().get(0).applied()).isTrue();
    }

    @Test
    @DisplayName("원문에 없는 before 는 적용하지 않고 applied=false 로 남긴다")
    void notFound() {
        ChangeApplier.Result r = ChangeApplier.apply(RESUME, List.of(
                new ChangeApplier.Proposed(ChangeType.REPLACE, "", "쿠버네티스 운영", "EKS 운영", "")));

        assertThat(r.revisedText()).isEqualTo(RESUME);
        assertThat(r.changes()).extracting(Change::applied).containsExactly(false);
    }

    @Test
    @DisplayName("before 와 after 가 같으면 바꾼 것이 아니므로 적용하지 않는다")
    void sameText() {
        ChangeApplier.Result r = ChangeApplier.apply(RESUME, List.of(
                new ChangeApplier.Proposed(ChangeType.REPLACE, "", "Java", "Java", "")));

        assertThat(r.changes().get(0).applied()).isFalse();
    }

    @Test
    @DisplayName("변경은 순서대로 쌓인다 — 앞 변경의 결과에 다음 변경을 적용한다")
    void sequential() {
        ChangeApplier.Result r = ChangeApplier.apply(RESUME, List.of(
                new ChangeApplier.Proposed(ChangeType.REPLACE, "", "Java, Spring", "Java, Spring Boot", ""),
                new ChangeApplier.Proposed(ChangeType.REPLACE, "", "Spring Boot", "Spring Boot, JPA", "")));

        assertThat(r.revisedText()).endsWith("기술: Java, Spring Boot, JPA");
    }
}
