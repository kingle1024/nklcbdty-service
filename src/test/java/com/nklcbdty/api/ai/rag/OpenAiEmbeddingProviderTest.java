package com.nklcbdty.api.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OpenAI Embeddings API 호출/응답 매핑 검증. 실제 네트워크는 타지 않는다.
 *
 * <p>여기서 잡고 싶은 것: 요청 형식(모델·차원·Bearer)이 맞는지, 응답을 <b>입력 순서대로</b>
 * 되돌리는지, 그리고 실패해도 예외를 던지지 않고 null 로 흘려 인덱서가 다음 주기에
 * 재시도할 수 있는지.</p>
 */
class OpenAiEmbeddingProviderTest {

    private static final String URL = "https://api.openai.com/v1/embeddings";

    private RestTemplate rest;
    private MockRestServiceServer server;

    private OpenAiEmbeddingProvider provider(String apiKey, int dimensions) {
        rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        return new OpenAiEmbeddingProvider(apiKey, "text-embedding-3-small", dimensions, rest);
    }

    @Test
    @DisplayName("요청에 모델·차원·Bearer 키가 실린다")
    void 요청_형식() {
        OpenAiEmbeddingProvider p = provider("sk-test", 4);
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.dimensions").value(4))
                .andExpect(jsonPath("$.input[0]").value("백엔드 개발자"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"index\":0,\"embedding\":[1.0,0.0,0.0,0.0]}]}",
                        MediaType.APPLICATION_JSON));

        assertArrayEquals(new float[]{1, 0, 0, 0}, p.embed("백엔드 개발자"), 1e-6f);
        server.verify();
    }

    @Test
    @DisplayName("dimensions 가 0 이하면 요청에 담지 않는다 (모델 기본 차원)")
    void 차원_미지정() {
        OpenAiEmbeddingProvider p = provider("sk-test", 0);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.dimensions").doesNotExist())
                .andRespond(withSuccess(
                        "{\"data\":[{\"index\":0,\"embedding\":[0.5,0.5]}]}",
                        MediaType.APPLICATION_JSON));

        assertArrayEquals(new float[]{0.5f, 0.5f}, p.embed("검색어"), 1e-6f);
        assertEquals("text-embedding-3-small", p.modelVersion());
        server.verify();
    }

    @Test
    @DisplayName("modelVersion 에 차원이 들어간다 — 차원을 바꾸면 재인덱싱이 걸리도록")
    void 모델버전에_차원_포함() {
        assertEquals("text-embedding-3-small-512", provider("sk-test", 512).modelVersion());
        assertEquals("text-embedding-3-small-256", provider("sk-test", 256).modelVersion());
    }

    @Test
    @DisplayName("응답이 뒤섞여 와도 index 기준으로 입력 순서에 맞춰 돌려준다")
    void 순서_복원() {
        OpenAiEmbeddingProvider p = provider("sk-test", 2);
        // 응답에 usage/object 같은 모르는 필드가 섞여 있어도 파싱돼야 한다.
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"object":"list",
                 "data":[{"object":"embedding","index":1,"embedding":[0.0,1.0]},
                         {"object":"embedding","index":0,"embedding":[1.0,0.0]}],
                 "model":"text-embedding-3-small",
                 "usage":{"prompt_tokens":8,"total_tokens":8}}
                """, MediaType.APPLICATION_JSON));

        List<float[]> out = p.embedAll(List.of("첫번째", "두번째"));

        assertEquals(2, out.size());
        assertArrayEquals(new float[]{1, 0}, out.get(0), 1e-6f);
        assertArrayEquals(new float[]{0, 1}, out.get(1), 1e-6f);
        server.verify();
    }

    @Test
    @DisplayName("빈 입력은 API 로 보내지 않고 그 자리만 null 로 남긴다")
    void 빈_입력_제외() {
        OpenAiEmbeddingProvider p = provider("sk-test", 2);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.input.length()").value(1))
                .andExpect(jsonPath("$.input[0]").value("유효"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"index\":0,\"embedding\":[1.0,0.0]}]}",
                        MediaType.APPLICATION_JSON));

        List<float[]> out = p.embedAll(Arrays.asList("  ", "유효", null));

        assertEquals(3, out.size());
        assertNull(out.get(0));
        assertArrayEquals(new float[]{1, 0}, out.get(1), 1e-6f);
        assertNull(out.get(2));
        server.verify();
    }

    @Test
    @DisplayName("MAX_BATCH 를 넘으면 여러 요청으로 쪼갠다")
    void 배치_분할() {
        OpenAiEmbeddingProvider p = provider("sk-test", 1);
        int n = OpenAiEmbeddingProvider.MAX_BATCH + 3;

        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.input.length()").value(OpenAiEmbeddingProvider.MAX_BATCH))
                .andRespond(withSuccess(batchResponse(OpenAiEmbeddingProvider.MAX_BATCH), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.input.length()").value(3))
                .andRespond(withSuccess(batchResponse(3), MediaType.APPLICATION_JSON));

        List<String> texts = new ArrayList<>();
        for (int i = 0; i < n; i++) texts.add("공고 " + i);

        List<float[]> out = p.embedAll(texts);

        assertEquals(n, out.size());
        for (float[] v : out) assertArrayEquals(new float[]{1}, v, 1e-6f);
        server.verify();
    }

    @Test
    @DisplayName("입력이 너무 길면 잘라서 보낸다")
    void 긴_입력_절단() {
        OpenAiEmbeddingProvider p = provider("sk-test", 1);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.input[0]").value("가".repeat(OpenAiEmbeddingProvider.MAX_CHARS)))
                .andRespond(withSuccess(batchResponse(1), MediaType.APPLICATION_JSON));

        p.embed("가".repeat(OpenAiEmbeddingProvider.MAX_CHARS + 500));
        server.verify();
    }

    @Test
    @DisplayName("API 가 실패해도 예외를 던지지 않고 null 을 준다")
    void 호출_실패() {
        OpenAiEmbeddingProvider p = provider("sk-test", 2);
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertNull(p.embed("서버 개발자"));
        server.verify();
    }

    @Test
    @DisplayName("API 키가 없으면 비가용 — 호출도 하지 않는다")
    void 키_없음() {
        OpenAiEmbeddingProvider p = provider("  ", 2);

        assertFalse(p.isAvailable());
        assertNull(p.embed("서버 개발자"));
        assertTrue(p.embedAll(List.of("서버 개발자")).isEmpty());
        server.verify(); // 기대한 요청이 없으므로 실제 호출이 있었다면 여기서 실패한다
    }

    private String batchResponse(int count) {
        StringBuilder b = new StringBuilder("{\"data\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) b.append(',');
            b.append("{\"index\":").append(i).append(",\"embedding\":[1.0]}");
        }
        return b.append("]}").toString();
    }
}
