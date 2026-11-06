package com.nklcbdty.api.resumereview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class GeminiJsonClientTest {

    private HttpServer server;
    private String baseUrl;
    /** 모델 이름 → 돌려줄 [상태코드, 본문] */
    private final Map<String, Object[]> replies = new ConcurrentHashMap<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String model = path.substring(path.lastIndexOf('/') + 1, path.indexOf(':'));
            calls.add(model);
            Object[] reply = replies.getOrDefault(model, new Object[] {404, "{}"});
            if ((Integer) reply[0] == 0) {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                reply = new Object[] {200, answer("STOP", "{\"text\":\"{}\"}")};
            }
            byte[] body = ((String) reply[1]).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders((Integer) reply[0], body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/models/";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String answer(String finishReason, String... texts) {
        StringBuilder parts = new StringBuilder();
        for (int i = 0; i < texts.length; i++) {
            if (i > 0) {
                parts.append(',');
            }
            parts.append(texts[i]);
        }
        return "{\"candidates\":[{\"finishReason\":\"" + finishReason + "\",\"content\":{\"parts\":[" + parts + "]}}]}";
    }

    @Test
    @DisplayName("혼잡(503)한 모델은 한 번 더 부르고, 그래도 안 되면 다음 모델로 넘어간다")
    void fallsBackOnBusy() {
        replies.put("busy", new Object[] {503, "{\"error\":{}}"});
        replies.put("ok", new Object[] {200, answer("STOP", "{\"text\":\"{\\\"a\\\":1}\"}")});

        GeminiJsonClient.Answer a = new GeminiJsonClient("k", baseUrl, "busy,ok").generate("p");

        assertThat(a.model()).isEqualTo("ok");
        assertThat(a.json()).isEqualTo("{\"a\":1}");
        assertThat(calls).containsExactly("busy", "busy", "ok");
    }

    @Test
    @DisplayName("시간 안에 답하지 않는 모델은 다시 부르지 않고 다음 모델로 넘어간다")
    void timeoutMovesOn() {
        replies.put("slow", new Object[] {0, ""});
        replies.put("ok", new Object[] {200, answer("STOP", "{\"text\":\"{}\"}")});

        GeminiJsonClient.Answer a = new GeminiJsonClient("k", baseUrl, "slow,ok",
                java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(10)).generate("p");

        assertThat(a.model()).isEqualTo("ok");
        assertThat(calls).containsExactly("slow", "ok");
    }

    @Test
    @DisplayName("400 같은 오류는 다시 부르지 않고 바로 다음 모델로 간다")
    void noRetryOnClientError() {
        replies.put("bad", new Object[] {400, "{}"});
        replies.put("ok", new Object[] {200, answer("STOP", "{\"text\":\"{}\"}")});

        new GeminiJsonClient("k", baseUrl, "bad,ok").generate("p");

        assertThat(calls).containsExactly("bad", "ok");
    }

    @Test
    @DisplayName("생각(thought) 부분은 빼고, 잘린 답(MAX_TOKENS)은 버린다")
    void thoughtsAndTruncation() {
        replies.put("cut", new Object[] {200, answer("MAX_TOKENS", "{\"text\":\"{\\\"a\\\":\"}")});
        replies.put("think", new Object[] {200, answer("STOP",
                "{\"text\":\"생각중\",\"thought\":true}", "{\"text\":\"{\\\"b\\\":2}\"}")});

        GeminiJsonClient.Answer a = new GeminiJsonClient("k", baseUrl, "cut,think").generate("p");

        assertThat(a.model()).isEqualTo("think");
        assertThat(a.json()).isEqualTo("{\"b\":2}");
    }

    @Test
    @DisplayName("모든 모델이 실패하면 혼잡 안내로 끝난다. 키가 없으면 부르지도 않는다")
    void allFail() {
        assertThatThrownBy(() -> new GeminiJsonClient("k", baseUrl, "x,y").generate("p"))
                .isInstanceOf(ResumeReviewException.class)
                .hasMessageContaining("혼잡");
        assertThatThrownBy(() -> new GeminiJsonClient("", baseUrl, "x").generate("p"))
                .hasMessageContaining("설정되지 않았");
        assertThat(calls).containsExactly("x", "y");
    }
}
