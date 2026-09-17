package com.nklcbdty.api.ai.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Embeddings API 기반 임베딩 공급자.
 *
 * <p>로컬 모델({@link LocalDjlEmbeddingProvider})과 달리 서버 메모리·디스크를 쓰지 않아
 * 0.5GB 급 컨테이너에서도 동작한다. 대신 입력 텍스트가 외부로 나가고 호출당 지연이 붙는다.</p>
 *
 * <p>모델 기본값은 {@code text-embedding-3-small} 이고, 차원은
 * {@code nklcb.rag.embedding.openai.dimensions} 로 줄인다 —
 * {@link JobEmbeddingCache} 가 전 공고의 벡터를 힙에 들고 있어서
 * 공고당 {@code dimensions * 4} byte 가 그대로 메모리 비용이 되기 때문이다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nklcb.rag.embedding.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private static final String ENDPOINT = "https://api.openai.com/v1/embeddings";

    /** 한 요청에 넣는 최대 입력 수. 인덱서 배치 크기(50)와 맞춰 두면 배치당 호출 1회로 끝난다. */
    static final int MAX_BATCH = 50;

    /**
     * 입력 문자 수 상한. 모델 상한은 8191 토큰이지만 한국어는 문자당 토큰 소모가 크다.
     * 이력서는 {@link ResumePdfExtractor} 가 이미 3000자로 자르므로 여기서는 안전망이다.
     */
    static final int MAX_CHARS = 6000;

    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final RestTemplate rest;

    public OpenAiEmbeddingProvider(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${nklcb.rag.embedding.openai.model:text-embedding-3-small}") String model,
            @Value("${nklcb.rag.embedding.openai.dimensions:512}") int dimensions) {
        this(apiKey, model, dimensions, defaultRestTemplate());
    }

    /** 테스트에서 RestTemplate 을 갈아끼우기 위한 생성자. */
    OpenAiEmbeddingProvider(String apiKey, String model, int dimensions, RestTemplate rest) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.dimensions = dimensions;
        this.rest = rest;

        if (this.apiKey.isBlank()) {
            log.warn("OpenAI API 키 미설정(spring.ai.openai.api-key) → 의미 검색/이력서 매칭 미동작");
        } else {
            log.info("임베딩 provider=openai model={} dimensions={}",
                    model, dimensions > 0 ? String.valueOf(dimensions) : "모델기본값");
        }
    }

    /**
     * 공용 RestTemplate({@code RestTemplateConfig}) 은 응답 타임아웃이 5초다.
     * 50건 배치 임베딩은 그보다 오래 걸릴 수 있어 전용 인스턴스를 쓴다
     * (공용 빈의 타임아웃을 늘리면 카카오 로그인 등 다른 호출까지 영향을 받는다).
     */
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
    public float[] embed(String text) {
        if (text == null || text.isBlank()) return null;
        List<float[]> out = embedAll(List.of(text));
        return out.isEmpty() ? null : out.get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (!isAvailable() || texts == null || texts.isEmpty()) return List.of();

        List<float[]> out = new ArrayList<>(Collections.nCopies(texts.size(), null));

        // 빈 문자열은 API 가 400 으로 거절한다 → 보내지 않고 해당 위치를 null 로 남긴다.
        List<Integer> positions = new ArrayList<>(texts.size());
        List<String> inputs = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t == null || t.isBlank()) continue;
            positions.add(i);
            inputs.add(clip(t));
        }

        for (int from = 0; from < inputs.size(); from += MAX_BATCH) {
            int to = Math.min(from + MAX_BATCH, inputs.size());
            float[][] vectors = callApi(inputs.subList(from, to));
            if (vectors == null) continue; // 이 청크만 실패 → 다음 주기에 다시 시도된다
            for (int i = 0; i < vectors.length; i++) {
                if (vectors[i] != null) out.set(positions.get(from + i), vectors[i]);
            }
        }
        return out;
    }

    /** 한 청크를 임베딩한다. 청크 전체가 실패하면 null. */
    private float[][] callApi(List<String> inputs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", inputs);
        body.put("encoding_format", "float");
        // dimensions 는 text-embedding-3-* 만 받는다. 0 이하면 모델 기본 차원을 쓴다.
        if (dimensions > 0) body.put("dimensions", dimensions);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            EmbeddingResponse res =
                    rest.postForObject(ENDPOINT, new HttpEntity<>(body, headers), EmbeddingResponse.class);
            if (res == null || res.data() == null || res.data().isEmpty()) {
                log.warn("임베딩 응답이 비어 있음 (inputs={})", inputs.size());
                return null;
            }

            float[][] out = new float[inputs.size()][];
            for (EmbeddingData d : res.data()) {
                // data 는 요청 순서대로 오지만, 순서를 믿지 않고 index 로 되돌린다.
                if (d == null || d.embedding() == null) continue;
                if (d.index() < 0 || d.index() >= out.length) continue;
                out[d.index()] = d.embedding();
            }
            return out;
        } catch (RestClientException e) {
            // 401(키 오류)·429(레이트리밋/쿼터)·5xx·타임아웃이 모두 여기로 온다.
            // 앱을 죽이지 않고 다음 주기에 재시도하도록 null 만 돌려준다.
            log.warn("임베딩 API 호출 실패 (inputs={}): {}", inputs.size(), e.getMessage());
            return null;
        }
    }

    private String clip(String text) {
        String t = text.trim();
        return t.length() <= MAX_CHARS ? t : t.substring(0, MAX_CHARS);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(int index, float[] embedding) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<EmbeddingData> data) {}
}
