package com.nklcbdty.api.ai.nlp;

import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.Span;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenNLP NER 모델 기반 경력 연차 추출기.
 *
 * <p>학습된 {@code nlp/personal_history_ner.bin} 모델을 클래스패스에서 로드해
 * "min" / "max" 엔티티 스팬을 인식하고, 스팬 텍스트에서 첫 숫자를 추출해 from/to를 채웁니다.</p>
 *
 * <p>모델이 존재하지 않으면(아직 학습 안 한 경우) 빈 결과를 반환합니다.
 * 이 경우 앙상블({@link PersonalHistoryEnsemble})이 규칙 기반 결과로 fallback 합니다.</p>
 */
@Slf4j
@Service
public class PersonalHistoryNerService {

    private static final String MODEL_PATH = "nlp/personal_history_ner.bin";
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    private NameFinderME nameFinder;

    @PostConstruct
    public void load() {
        ClassPathResource res = new ClassPathResource(MODEL_PATH);
        if (!res.exists()) {
            log.warn("NER 모델 파일이 없습니다 ({}). PersonalHistoryNerTrainer.main()으로 먼저 학습하세요.", MODEL_PATH);
            return;
        }
        try (InputStream in = res.getInputStream()) {
            TokenNameFinderModel model = new TokenNameFinderModel(in);
            this.nameFinder = new NameFinderME(model);
            log.info("NER 모델 로드 완료: {}", MODEL_PATH);
        } catch (Exception e) {
            log.error("NER 모델 로드 실패: {}", e.getMessage(), e);
        }
    }

    public boolean isAvailable() {
        return nameFinder != null;
    }

    public PersonalHistoryDto extract(String text) {
        PersonalHistoryDto result = new PersonalHistoryDto();
        result.setFrom(0);
        result.setTo(0);

        if (nameFinder == null || text == null || text.isBlank()) {
            return result;
        }

        String normalized = WHITESPACE.matcher(text).replaceAll(" ").trim();
        String[] tokens = normalized.split(" ");
        if (tokens.length == 0) return result;

        Span[] spans;
        synchronized (this) {
            spans = nameFinder.find(tokens);
            nameFinder.clearAdaptiveData();
        }

        long min = 0;
        long max = 0;
        for (Span s : spans) {
            String phrase = joinTokens(tokens, s.getStart(), s.getEnd());
            long n = firstNumber(phrase);
            if (n <= 0) continue;
            if ("min".equals(s.getType())) {
                min = Math.max(min, n);
            } else if ("max".equals(s.getType())) {
                max = (max == 0) ? n : Math.min(max, n);
            }
        }

        if (min > 0 && max > 0 && min > max) {
            long tmp = min;
            min = max;
            max = tmp;
        }

        result.setFrom(min);
        result.setTo(max);
        return result;
    }

    private String joinTokens(String[] tokens, int start, int end) {
        StringBuilder b = new StringBuilder();
        for (int i = start; i < end && i < tokens.length; i++) {
            if (b.length() > 0) b.append(' ');
            b.append(tokens[i]);
        }
        return b.toString();
    }

    private long firstNumber(String s) {
        Matcher m = NUMBER.matcher(s);
        if (m.find()) {
            try {
                return Long.parseLong(m.group());
            } catch (NumberFormatException ignored) { }
        }
        return 0;
    }
}
