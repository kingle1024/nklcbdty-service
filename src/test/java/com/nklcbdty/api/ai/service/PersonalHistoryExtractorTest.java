package com.nklcbdty.api.ai.service;

import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalHistoryExtractorTest {

    private final PersonalHistoryExtractor extractor = new PersonalHistoryExtractor();

    private void assertResult(String text, long expectedFrom, long expectedTo) {
        PersonalHistoryDto r = extractor.extract(text);
        assertEquals(expectedFrom, r.getFrom(), "from mismatch for: " + text);
        assertEquals(expectedTo, r.getTo(), "to mismatch for: " + text);
    }

    @Test
    @DisplayName("한국어: N년 이상")
    void koMin() {
        assertResult("경력 3년 이상", 3, 0);
        assertResult("개발 경력 5년 이상이신 분", 5, 0);
        assertResult("3년차 이상", 3, 0);
    }

    @Test
    @DisplayName("한국어: N년 이하 / 미만")
    void koMax() {
        assertResult("경력 5년 이하", 0, 5);
        assertResult("5년 미만의 경력자", 0, 5);
    }

    @Test
    @DisplayName("한국어: 범위 패턴")
    void koRange() {
        assertResult("1년 이상 5년 이하", 1, 5);
        assertResult("경력 3년 ~ 7년", 3, 7);
        assertResult("3년~7년", 3, 7);
        assertResult("3년-7년", 3, 7);
        assertResult("3년차 ~ 5년차", 3, 5);
    }

    @Test
    @DisplayName("한국어: 최소 N년 / 경력 N년")
    void koMinAlt() {
        assertResult("최소 3년의 백엔드 개발 경험", 3, 0);
        assertResult("경력 5년", 5, 0);
    }

    @Test
    @DisplayName("영어: N+ years / Over / At least / Minimum")
    void enMin() {
        assertResult("5+ years of experience", 5, 0);
        assertResult("Over 7 years of relevant experience", 7, 0);
        assertResult("At least 3 years in backend", 3, 0);
        assertResult("Minimum 5 years required", 5, 0);
        assertResult("Min. 4 yrs experience", 4, 0);
        assertResult("3 or more years", 3, 0);
    }

    @Test
    @DisplayName("영어: Less than / Up to / Maximum")
    void enMax() {
        assertResult("Less than 3 years of experience", 0, 3);
        assertResult("Up to 5 years", 0, 5);
        assertResult("Maximum 4 years", 0, 4);
    }

    @Test
    @DisplayName("영어: 범위")
    void enRange() {
        assertResult("3 to 5 years of experience", 3, 5);
        assertResult("3-5 years", 3, 5);
        assertResult("Between 2 and 7 years", 2, 7);
    }

    @Test
    @DisplayName("신입 / 경력 무관 / Entry-level → (0,0)")
    void entryLevel() {
        assertResult("신입사원 모집", 0, 0);
        assertResult("경력 무관", 0, 0);
        assertResult("Entry-level developer position", 0, 0);
        assertResult("No experience required", 0, 0);
    }

    @Test
    @DisplayName("미명시 / 빈 입력")
    void none() {
        assertResult("프론트엔드 개발자 채용공고", 0, 0);
        assertResult("", 0, 0);
        assertResult(null, 0, 0);
    }

    @Test
    @DisplayName("긴 페이지 텍스트에서도 정확히 추출")
    void longPageText() {
        String body = "회사 소개 ... 우리는 핀테크 스타트업입니다. " +
                "[자격요건] 백엔드 개발 경력 3년 이상 보유하신 분 " +
                "[우대사항] AWS 경험자 ... 복리후생 ... ";
        assertResult(body, 3, 0);
    }

    @Test
    @DisplayName("한/영 혼합 공고: 범위 우선")
    void mixedRange() {
        String body = "We are hiring backend engineers. " +
                "Required: 3 to 5 years of professional experience. " +
                "한국어: 경력 3년 이상.";
        PersonalHistoryDto r = extractor.extract(body);
        assertEquals(3, r.getFrom());
        assertEquals(5, r.getTo());
    }

    @Test
    @DisplayName("min > max인 경우 swap")
    void swapWhenMinGreater() {
        assertResult("경력 10년 이상 3년 이하", 3, 10);
    }

    @Test
    @DisplayName("여러 '이상' 조건은 모두 만족해야 하므로 가장 큰 값 채택")
    void multipleMinsTakesMax() {
        // 실제 공고 케이스: 과거 버전에서는 2로 잡혔던 버그
        String text = "소프트웨어 개발 경험 10년 이상 " +
                "매니저 관리를 포함한 관리 경험 2년 이상 (멘토, 테크 리드 경험 포함)";
        assertResult(text, 10, 0);

        // 영어 혼합도 동일 정책
        assertResult("5+ years of backend experience, 3+ years of cloud experience", 5, 0);
    }
}
