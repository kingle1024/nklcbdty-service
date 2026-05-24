package com.nklcbdty.api.ai.nlp;

import com.nklcbdty.api.ai.service.PersonalHistoryExtractor;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 규칙 기반 추출기({@link PersonalHistoryExtractor})와
 * NER 기반 추출기({@link PersonalHistoryNerService})를 결합한 앙상블.
 *
 * <p>정책:</p>
 * <ol>
 *   <li>두 결과가 일치 → 그 결과 채택 (신뢰도 ↑)</li>
 *   <li>규칙 기반이 (0,0)이고 ML이 무언가를 잡았다면 → ML 결과 채택</li>
 *   <li>그 외는 규칙 기반 우선 (현시점에서는 더 안정적)</li>
 *   <li>불일치 시 디버그 로깅 → 추후 학습 데이터 보강용 시그널로 활용</li>
 * </ol>
 *
 * <p>ML 모델 학습이 충분히 진행되면 우선순위를 ML 쪽으로 옮기는 것이 자연스러운 진화 경로입니다.</p>
 */
@Slf4j
@Service
public class PersonalHistoryEnsemble {

    private final PersonalHistoryExtractor ruleBased;
    private final PersonalHistoryNerService mlBased;

    public PersonalHistoryEnsemble(PersonalHistoryExtractor ruleBased,
                                   PersonalHistoryNerService mlBased) {
        this.ruleBased = ruleBased;
        this.mlBased = mlBased;
    }

    public PersonalHistoryDto extract(String text) {
        PersonalHistoryDto rule = ruleBased.extract(text);

        if (!mlBased.isAvailable()) {
            return rule;
        }

        PersonalHistoryDto ml = mlBased.extract(text);

        boolean ruleEmpty = rule.getFrom() == 0 && rule.getTo() == 0;
        boolean mlEmpty = ml.getFrom() == 0 && ml.getTo() == 0;

        if (rule.getFrom() == ml.getFrom() && rule.getTo() == ml.getTo()) {
            return rule;
        }

        log.debug("앙상블 불일치 - rule=({},{}) ml=({},{}) text='{}'",
                rule.getFrom(), rule.getTo(), ml.getFrom(), ml.getTo(),
                snippet(text));

        if (ruleEmpty && !mlEmpty) {
            return ml;
        }
        return rule;
    }

    private String snippet(String text) {
        if (text == null) return "";
        String s = text.replaceAll("\\s+", " ").trim();
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
