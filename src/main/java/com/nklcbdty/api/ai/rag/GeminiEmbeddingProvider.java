package com.nklcbdty.api.ai.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini 임베딩 공급자 (기본값).
 *
 * <p>OpenAI 임베딩은 유료라 크레딧이 떨어지면 429 로 인덱싱이 통째로 멈췄다(2026-10-06, 11-09).
 * Gemini 는 무료 등급이 있고, 키({@code GEMINI_API_KEY})는 이력서 첨삭이 이미 쓰고 있다.</p>
 *
 * <p>{@code batchEmbedContents} 로 한 번에 최대 {@link #MAX_BATCH}건을 보낸다.
 * 차원은 {@code outputDimensionality} 로 줄인다 — 줄인 벡터는 정규화돼 있지 않지만
 * {@link EmbeddingService} 가 정규화한다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nklcb.rag.embedding.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiEmbeddingProvider implements EmbeddingProvider {

    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    /** 인덱서 배치 크기(50)와 맞춘다. API 상한은 100. */
    static final int MAX_BATCH = 50;

    /** 입력 상한 2048 토큰. 한국어는 글자당 토큰이 많아 보수적으로 자른다. */
    static final int MAX_CHARS = 3000;

    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final RestTemplate rest;

    private volatile String lastFailure;

    @Autowired
    public GeminiEmbeddingProvider(
            @Value("${google.gemini.api-key:}") String apiKey,
            @Value("${nklcb.rag.embedding.gemini.model:gemini-embedding-001}") String model,
            @Value("${nklcb.rag.embedding.gemini.dimensions:512}") int dimensions) {
        this(apiKey, model, dimensions, defaultRestTemplate());
    }

    GeminiEmbeddingProvider(String apiKey, String model, int dimensions, RestTemplate rest) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.dimensions = dimensions;
        this.rest = rest;
        if (this.apiKey.isBlank()) {
            log.warn("Gemini API 키 미설정(GEMINI_API_KEY) → 의미 검색은 키워드 매칭으로 대신한다");
        } else {
            log.info("임베딩 provider=gemini model={} dimensions={}", model, dimensions);
        }
    }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isBlank();
    }

    @Override
    public String modelVersion() {
        return dimensions > 0 ? model + "-" + dimensions : model;
    }

    @Override
    public String lastFailure() {
        return lastFailure;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) return null;
        List<float[]> out = embedAll(List.of(text));
        return out.isEmpty() ? null : out.get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (!isAvailable() || texts == null || texts.isEmpty()) return List.of();

        List<float[]> out = new ArrayList<>(Collections.nCopies(texts.size(), null));
        List<Integer> positions = new ArrayList<>(texts.size());
        List<String> inputs = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t == null || t.isBlank()) continue;
            positions.add(i);
            String s = t.trim();
            inputs.add(s.length() <= MAX_CHARS ? s : s.substring(0, MAX_CHARS));
        }

        for (int from = 0; from < inputs.size(); from += MAX_BATCH) {
            int to = Math.min(from + MAX_BATCH, inputs.size());
            float[][] vectors = callApi(inputs.subList(from, to));
            if (vectors == null) continue;
            for (int i = 0; i < vectors.length; i++) {
                if (vectors[i] != null) out.set(positions.get(from + i), vectors[i]);
            }
        }
        return out;
    }

    private float[][] callApi(List<String> inputs) {
        List<Map<String, Object>> requests = new ArrayList<>(inputs.size());
        for (String text : inputs) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("model", "models/" + model);
            r.put("content", Map.of("parts", List.of(Map.of("text", text))));
            // 이력서와 공고를 같은 공간에서 견주므로 질의/문서를 나누지 않고 유사도용으로 맞춘다.
            r.put("taskType", "SEMANTIC_SIMILARITY");
            if (dimensions > 0) r.put("outputDimensionality", dimensions);
            requests.add(r);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 키를 URL(?key=)에 넣지 않는다 — 실패 로그에 주소가 찍히면 키가 같이 남는다.
        headers.set("x-goog-api-key", apiKey);

        try {
            BatchResponse res = rest.postForObject(BASE_URL + model + ":batchEmbedContents",
                    new HttpEntity<>(Map.of("requests", requests), headers), BatchResponse.class);
            if (res == null || res.embeddings() == null || res.embeddings().isEmpty()) {
                log.warn("Gemini 임베딩 응답이 비어 있음 (inputs={})", inputs.size());
                recordFailure("빈 응답");
                return null;
            }
            // 응답은 요청 순서 그대로 온다(인덱스 필드가 없다).
            float[][] out = new float[inputs.size()][];
            for (int i = 0; i < Math.min(out.length, res.embeddings().size()); i++) {
                Embedding e = res.embeddings().get(i);
                if (e != null && e.values() != null && e.values().length > 0) out[i] = e.values();
            }
            lastFailure = null;
            return out;
        } catch (HttpStatusCodeException e) {
            // 본문은 남기지 않는다(공개되는 lastFailure 에는 상태 코드만).
            log.warn("Gemini 임베딩 호출 실패 (inputs={}): {}", inputs.size(), e.getStatusCode());
            recordFailure("HTTP " + e.getStatusCode());
            return null;
        } catch (RestClientException e) {
            log.warn("Gemini 임베딩 호출 실패 (inputs={}): {}", inputs.size(), e.getClass().getSimpleName());
            recordFailure(e.getClass().getSimpleName());
            return null;
        }
    }

    private void recordFailure(String what) {
        lastFailure = what + " @ " + LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Embedding(float[] values) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchResponse(List<Embedding> embeddings) {}
}
