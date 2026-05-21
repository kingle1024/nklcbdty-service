package com.nklcbdty.api.ai.service;

import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채용공고 텍스트에서 요구 경력 연차(최소~최대)를 추출하는 자체 NLP 추출기.
 *
 * - 한국어/영어 패턴 모두 지원
 * - 결과는 PersonalHistoryDto(from, to)로 반환
 *   - from > 0, to = 0  : "N년 이상"
 *   - from = 0, to > 0  : "N년 이하"
 *   - from > 0, to > 0  : 범위
 *   - from = 0, to = 0  : 미명시 / 신입 / 무관
 *
 * Gemini 같은 외부 LLM 호출 비용 없이 처리하기 위한 도메인 특화 규칙 엔진.
 */
@Slf4j
@Service
public class PersonalHistoryExtractor {

    private static final Pattern ENTRY_LEVEL = Pattern.compile(
            "신입(?!.{0,10}(경력|이상|년))|" +
            "경력\\s*무관|" +
            "경력\\s*없음|" +
            "무경험|" +
            "no\\s*experience(\\s*required)?|" +
            "entry[\\-\\s]?level|" +
            "junior\\s*level",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern KO_RANGE = Pattern.compile(
            "(\\d+)\\s*년\\s*(?:차)?\\s*[~\\-–—]+\\s*(\\d+)\\s*년\\s*(?:차)?");

    private static final Pattern KO_MIN = Pattern.compile(
            "(\\d+)\\s*년\\s*(?:차)?\\s*(?:이상|초과|\\+)");

    private static final Pattern KO_MIN_ALT = Pattern.compile(
            "최소\\s*(\\d+)\\s*년|" +
            "경력\\s*(\\d+)\\s*년(?!\\s*(?:이하|미만))(?:\\s*이상)?");

    private static final Pattern KO_MAX = Pattern.compile(
            "(\\d+)\\s*년\\s*(?:차)?\\s*(?:이하|미만)");

    private static final Pattern EN_RANGE = Pattern.compile(
            "(\\d+)\\s*(?:to|\\-|–|~)\\s*(\\d+)\\s*\\+?\\s*(?:years?|yrs?)|" +
            "between\\s*(\\d+)\\s*and\\s*(\\d+)\\s*(?:years?|yrs?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EN_MIN = Pattern.compile(
            "(\\d+)\\s*\\+\\s*(?:years?|yrs?)|" +
            "(?:over|more\\s+than|at\\s+least|minimum(?:\\s+of)?|min\\.?|no\\s+less\\s+than)\\s*(\\d+)\\s*(?:years?|yrs?)|" +
            "(\\d+)\\s*(?:years?|yrs?)\\s*(?:or\\s+more|or\\s+above|and\\s+above|minimum)|" +
            "(\\d+)\\s*(?:or\\s+more|or\\s+above|and\\s+above)\\s*(?:years?|yrs?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EN_MAX = Pattern.compile(
            "(?:less\\s+than|under|up\\s+to|maximum(?:\\s+of)?|max\\.?|no\\s+more\\s+than)\\s*(\\d+)\\s*(?:years?|yrs?)|" +
            "(\\d+)\\s*(?:years?|yrs?)\\s*(?:or\\s+less)",
            Pattern.CASE_INSENSITIVE);

    public PersonalHistoryDto extract(String text) {
        PersonalHistoryDto result = new PersonalHistoryDto();
        result.setFrom(0);
        result.setTo(0);

        if (text == null || text.isBlank()) {
            return result;
        }

        String normalized = normalize(text);

        long minYears = 0;
        long maxYears = 0;

        Matcher koRange = KO_RANGE.matcher(normalized);
        if (koRange.find()) {
            minYears = safeLong(koRange.group(1));
            maxYears = safeLong(koRange.group(2));
        } else {
            Matcher enRange = EN_RANGE.matcher(normalized);
            if (enRange.find()) {
                String a = enRange.group(1) != null ? enRange.group(1) : enRange.group(3);
                String b = enRange.group(2) != null ? enRange.group(2) : enRange.group(4);
                minYears = safeLong(a);
                maxYears = safeLong(b);
            }
        }

        if (minYears == 0) {
            List<Long> mins = new ArrayList<>();
            collectGroup(KO_MIN, normalized, mins, 1);
            collectGroup(KO_MIN_ALT, normalized, mins, 1);
            collectGroup(KO_MIN_ALT, normalized, mins, 2);
            collectGroup(EN_MIN, normalized, mins, 1);
            collectGroup(EN_MIN, normalized, mins, 2);
            collectGroup(EN_MIN, normalized, mins, 3);
            collectGroup(EN_MIN, normalized, mins, 4);
            if (!mins.isEmpty()) {
                // 복수의 "N년 이상" 조건은 모두 만족해야 하므로 가장 큰 값을 채택.
                // (예: "10년 이상 + 2년 이상" → 10)
                minYears = mins.stream().max(Long::compare).orElse(0L);
            }
        }

        if (maxYears == 0) {
            List<Long> maxes = new ArrayList<>();
            collectGroup(KO_MAX, normalized, maxes, 1);
            collectGroup(EN_MAX, normalized, maxes, 1);
            collectGroup(EN_MAX, normalized, maxes, 2);
            if (!maxes.isEmpty()) {
                maxYears = maxes.stream().min(Long::compare).orElse(0L);
            }
        }

        if (minYears == 0 && maxYears == 0) {
            if (ENTRY_LEVEL.matcher(normalized).find()) {
                log.debug("신입/경력무관 신호만 감지됨 → (0,0) 반환");
            }
        }

        if (minYears > 0 && maxYears > 0 && minYears > maxYears) {
            long tmp = minYears;
            minYears = maxYears;
            maxYears = tmp;
        }

        result.setFrom(minYears);
        result.setTo(maxYears);
        return result;
    }

    private void collectGroup(Pattern p, String text, List<Long> out, int group) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            if (m.groupCount() >= group) {
                String g = m.group(group);
                if (g != null) {
                    long v = safeLong(g);
                    if (v > 0) out.add(v);
                }
            }
        }
    }

    private long safeLong(String s) {
        if (s == null) return 0;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            log.warn("숫자 변환 실패: '{}'", s);
            return 0;
        }
    }

    private String normalize(String text) {
        return text.replaceAll("[\\s\\u00A0]+", " ");
    }
}
