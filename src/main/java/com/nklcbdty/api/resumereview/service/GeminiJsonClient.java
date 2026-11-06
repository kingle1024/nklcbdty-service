package com.nklcbdty.api.resumereview.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 첨삭용 Gemini 호출. JSON 으로만 답하게 하고, 모델을 순서대로 시도한다.
 *
 * <p>직무 분류용 {@code GeminiService} 를 쓰지 않는 이유:</p>
 * <ul>
 *   <li>그쪽은 모델이 박힌 주소(GEMINI_API_URL) 하나만 쓴다. 그 모델이 내려가면(2.0-flash 처럼) 통째로 멈춘다.
 *       여기선 {@code -latest} 별칭을 써서 모델 교체를 따라가고, 혼잡하면(429/503) 다음 모델로 넘어간다.</li>
 *   <li>그쪽은 크롤 배치용으로 4.5초 간격 throttle 이 걸려 있다. 사용자가 기다리는 요청에 끼우면 안 된다.</li>
 * </ul>
 */
@Slf4j
@Component
public class GeminiJsonClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 한 모델이 이보다 오래 걸리면 혼잡한 것으로 보고 다음 모델로 넘어간다 */
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(35);
    /** 요청 하나가 AI 를 기다리는 총 시간. 사용자가 화면에서 기다리고, 게이트웨이도 무한정 기다려 주지 않는다 */
    private static final Duration DEFAULT_TOTAL_BUDGET = Duration.ofSeconds(80);
    private static final Duration MIN_USEFUL_TIMEOUT = Duration.ofMillis(500);
    private static final int ATTEMPTS_PER_MODEL = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);
    private static final String BUSY = "지금은 AI 가 혼잡해서 첨삭하지 못했습니다. 잠시 후 다시 시도해주세요.";

    private final HttpClient http;
    private final String apiKey;
    private final String baseUrl;
    private final List<String> models;
    private final Duration requestTimeout;
    private final Duration totalBudget;

    @Autowired
    public GeminiJsonClient(
            @Value("${google.gemini.api-key:}") String apiKey,
            @Value("${nklcb.resume-review.gemini-base-url:https://generativelanguage.googleapis.com/v1beta/models/}") String baseUrl,
            @Value("${nklcb.resume-review.models:gemini-flash-latest,gemini-3.1-flash-lite,gemini-flash-lite-latest}") String models) {
        this(apiKey, baseUrl, models, DEFAULT_REQUEST_TIMEOUT, DEFAULT_TOTAL_BUDGET);
    }

    /** 테스트에서 시간 상한을 줄이려고 둔다 */
    GeminiJsonClient(String apiKey, String baseUrl, String models, Duration requestTimeout, Duration totalBudget) {
        this.requestTimeout = requestTimeout;
        this.totalBudget = totalBudget;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.models = Arrays.stream(models.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    public record Answer(String model, String json) {
    }

    /** 한 번 부른 결과. text 가 있으면 성공, 없으면 retryable 로 같은 모델을 다시 부를지 정한다 */
    private record Outcome(String text, boolean retryable) {
    }

    /**
     * @return 처음 성공한 모델과 그 답(JSON 문자열)
     * @throws ResumeReviewException 모든 모델이 실패했을 때(UNAVAILABLE)
     */
    public Answer generate(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw ResumeReviewException.unavailable("AI 첨삭이 설정되지 않았습니다.");
        }
        final String body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))))
                .put("generationConfig", new JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("temperature", 0.3)
                        .put("maxOutputTokens", 16384))
                .toString();

        final long deadline = System.nanoTime() + totalBudget.toNanos();
        for (String model : models) {
            for (int attempt = 0; attempt < ATTEMPTS_PER_MODEL; attempt++) {
                final Duration left = Duration.ofNanos(deadline - System.nanoTime());
                if (left.compareTo(MIN_USEFUL_TIMEOUT) < 0) {
                    throw ResumeReviewException.unavailable(BUSY);
                }
                final Outcome outcome = call(model, body, left.compareTo(requestTimeout) < 0 ? left : requestTimeout);
                if (outcome.text() != null) {
                    return new Answer(model, outcome.text());
                }
                if (!outcome.retryable() || attempt == ATTEMPTS_PER_MODEL - 1) {
                    break;
                }
                // 혼잡(429/503)은 몇 초 안에 풀리는 일이 많다. 같은 모델을 조금 쉬었다가 다시 부른다.
                sleep(RETRY_BACKOFF.multipliedBy(attempt + 1));
            }
        }
        throw ResumeReviewException.unavailable(BUSY);
    }

    private Outcome call(String model, String body, Duration timeout) {
        final URI uri = URI.create(baseUrl + model + ":generateContent?key="
                                   + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        final HttpRequest request = HttpRequest.newBuilder(uri)
                                               .timeout(timeout)
                                               .header("Content-Type", "application/json")
                                               .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                                               .build();
        try {
            final HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                // 본문엔 키가 없지만 길어서 앞부분만 남긴다.
                log.warn("첨삭 Gemini 실패 model={} status={} body={}", model, res.statusCode(),
                         res.body().substring(0, Math.min(300, res.body().length())));
                final int s = res.statusCode();
                return new Outcome(null, s == 429 || s == 500 || s == 503);
            }
            return new Outcome(extractText(model, res.body()), false);
        } catch (HttpTimeoutException e) {
            // 오래 붙잡고 있던 모델을 또 부르면 남은 시간을 다 쓴다. 바로 다음 모델로 넘어간다.
            log.warn("첨삭 Gemini 시간 초과 model={} timeout={}s", model, timeout.toSeconds());
            return new Outcome(null, false);
        } catch (IOException e) {
            log.warn("첨삭 Gemini 호출 오류 model={} reason={}", model, e.toString());
            return new Outcome(null, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ResumeReviewException.unavailable("첨삭이 중단되었습니다. 다시 시도해주세요.");
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ResumeReviewException.unavailable("첨삭이 중단되었습니다. 다시 시도해주세요.");
        }
    }

    /** 생각(thought) 부분을 빼고 답 글자만 이어 붙인다. 잘린 답(MAX_TOKENS)은 JSON 이 깨져 있어 버린다. */
    static String extractText(String model, String responseJson) {
        final JSONObject root = new JSONObject(responseJson);
        final JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            log.warn("첨삭 Gemini 빈 답 model={}", model);
            return null;
        }
        final JSONObject first = candidates.getJSONObject(0);
        final String finish = first.optString("finishReason");
        if (!finish.isEmpty() && !"STOP".equals(finish)) {
            log.warn("첨삭 Gemini 비정상 종료 model={} finishReason={}", model, finish);
            return null;
        }
        final JSONObject content = first.optJSONObject("content");
        final JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            final JSONObject part = parts.optJSONObject(i);
            if (part != null && !part.optBoolean("thought")) {
                out.append(part.optString("text"));
            }
        }
        return out.length() == 0 ? null : out.toString();
    }
}
