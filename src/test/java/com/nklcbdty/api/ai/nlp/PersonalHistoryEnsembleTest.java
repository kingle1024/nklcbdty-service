package com.nklcbdty.api.ai.nlp;

import com.nklcbdty.api.ai.service.PersonalHistoryExtractor;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalHistoryEnsembleTest {

    private final PersonalHistoryExtractor rule = new PersonalHistoryExtractor();

    private PersonalHistoryEnsemble newEnsemble(boolean mlAvailable, long mlFrom, long mlTo) {
        PersonalHistoryNerService ml = new PersonalHistoryNerService() {
            @Override public boolean isAvailable() { return mlAvailable; }
            @Override public PersonalHistoryDto extract(String text) {
                PersonalHistoryDto d = new PersonalHistoryDto();
                d.setFrom(mlFrom);
                d.setTo(mlTo);
                return d;
            }
        };
        return new PersonalHistoryEnsemble(rule, ml);
    }

    @Test
    @DisplayName("ML 모델 미로드 시 규칙 기반 결과 그대로 사용")
    void mlUnavailable_fallbackToRule() {
        PersonalHistoryEnsemble e = newEnsemble(false, 99, 99);
        PersonalHistoryDto r = e.extract("경력 3년 이상");
        assertEquals(3, r.getFrom());
        assertEquals(0, r.getTo());
    }

    @Test
    @DisplayName("규칙=ML 일치 시 그대로 반환")
    void agreement() {
        PersonalHistoryEnsemble e = newEnsemble(true, 3, 0);
        PersonalHistoryDto r = e.extract("경력 3년 이상");
        assertEquals(3, r.getFrom());
        assertEquals(0, r.getTo());
    }

    @Test
    @DisplayName("규칙이 비었고 ML이 잡았으면 ML 채택")
    void ruleEmpty_mlWins() {
        PersonalHistoryEnsemble e = newEnsemble(true, 5, 0);
        // 규칙 기반이 못 잡는 표현 (가상)
        PersonalHistoryDto r = e.extract("백엔드 시니어 개발자 채용");
        assertEquals(5, r.getFrom());
        assertEquals(0, r.getTo());
    }

    @Test
    @DisplayName("불일치 시 규칙 기반 우선 (안정성 우선)")
    void disagreement_ruleWins() {
        PersonalHistoryEnsemble e = newEnsemble(true, 7, 0);
        PersonalHistoryDto r = e.extract("경력 3년 이상");
        assertEquals(3, r.getFrom());
        assertEquals(0, r.getTo());
    }
}
