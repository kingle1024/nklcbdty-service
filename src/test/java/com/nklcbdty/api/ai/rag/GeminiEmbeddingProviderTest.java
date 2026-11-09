package com.nklcbdty.api.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Gemini batchEmbedContents 요청 형식·응답 매핑·실패 처리. 네트워크는 타지 않는다. */
class GeminiEmbeddingProviderTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents";

    private MockRestServiceServer server;

    private GeminiEmbeddingProvider provider(String key) {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        return new GeminiEmbeddingProvider(key, "gemini-embedding-001", 4, rest);
    }

    @Test
    @DisplayName("키는 헤더로(URL 에 안 싣는다), 모델·차원·본문이 요청마다 실린다")
    void 요청_형식() {
        GeminiEmbeddingProvider p = provider("g-key");
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "g-key"))
                .andExpect(jsonPath("$.requests[0].model").value("models/gemini-embedding-001"))
                .andExpect(jsonPath("$.requests[0].outputDimensionality").value(4))
                .andExpect(jsonPath("$.requests[0].content.parts[0].text").value("백엔드 개발자"))
                .andRespond(withSuccess("{\"embeddings\":[{\"values\":[1.0,0.0,0.0,0.0]}]}",
                        MediaType.APPLICATION_JSON));

        assertArrayEquals(new float[]{1f, 0f, 0f, 0f}, p.embed("백엔드 개발자"));
        server.verify();
    }

    @Test
    @DisplayName("빈 입력은 보내지 않고 그 자리만 null — 나머지는 입력 순서대로")
    void 순서와_빈입력() {
        GeminiEmbeddingProvider p = provider("g-key");
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.requests.length()").value(2))
                .andRespond(withSuccess("{\"embeddings\":[{\"values\":[1,0,0,0]},{\"values\":[0,1,0,0]}]}",
                        MediaType.APPLICATION_JSON));

        List<float[]> out = p.embedAll(Arrays.asList("a", " ", "b"));
        assertEquals(3, out.size());
        assertArrayEquals(new float[]{1, 0, 0, 0}, out.get(0));
        assertNull(out.get(1));
        assertArrayEquals(new float[]{0, 1, 0, 0}, out.get(2));
    }

    @Test
    @DisplayName("429 면 예외 없이 null 을 주고 상태 코드만 lastFailure 에 남긴다")
    void 실패() {
        GeminiEmbeddingProvider p = provider("g-key");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"key\":\"secret\"}"));

        assertNull(p.embed("x"));
        assertTrue(p.lastFailure().startsWith("HTTP 429"));
        assertFalse(p.lastFailure().contains("secret"));
    }

    @Test
    @DisplayName("키가 없으면 비가용이고, 모델 버전에 차원이 들어간다")
    void 키없음_버전() {
        GeminiEmbeddingProvider p = provider("");
        assertFalse(p.isAvailable());
        assertTrue(p.embedAll(List.of("x")).isEmpty());
        assertEquals("gemini-embedding-001-4", p.modelVersion());
    }
}
