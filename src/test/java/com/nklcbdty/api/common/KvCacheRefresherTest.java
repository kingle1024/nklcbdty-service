package com.nklcbdty.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.nklcbdty.api.crawler.service.JobService;

// 프론트(kvCache.ts)는 /kv 를 먼저 읽고 미스일 때만 원본 API 로 폴백한다. 크롤이 KV 를 갱신하지
// 않으면 새 공고가 TTL(6시간) 동안 화면에 안 나온다 — 배민 0건 사태의 마지막 원인이었다.
class KvCacheRefresherTest {

    private RestTemplate restTemplate;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        jobService = mock(JobService.class);
        when(jobService.listAsJson(anyString())).thenReturn("[]");
    }

    private KvCacheRefresher enabled() {
        return new KvCacheRefresher(restTemplate, jobService, "10.0.0.5", "cache.example.com");
    }

    private List<String> capturedUrls() {
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, org.mockito.Mockito.atLeastOnce())
            .exchange(url.capture(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
        return new ArrayList<>(url.getAllValues());
    }

    @Test
    @DisplayName("회사 단건 크롤 뒤에는 그 회사 키와 ALL 만 갱신한다")
    void refreshesCompanyAndAllOnly() {
        enabled().refreshAfterCrawl("BAEMIN");

        List<String> urls = capturedUrls();
        assertThat(urls).hasSize(2);
        assertThat(urls).anyMatch(u -> u.contains("nklcb:list:BAEMIN"));
        assertThat(urls).anyMatch(u -> u.contains("nklcb:list:ALL"));
        // 안 바뀐 회사까지 다시 만들면 크롤 응답만 느려진다.
        assertThat(urls).noneMatch(u -> u.contains("nklcb:list:NAVER"));
    }

    @Test
    @DisplayName("컨트롤러 파라미터가 소문자여도 KV 키는 대문자로 맞춘다")
    void normalizesCompanyCode() {
        enabled().refreshAfterCrawl("baemin");

        assertThat(capturedUrls()).anyMatch(u -> u.contains("nklcb:list:BAEMIN"));
    }

    @Test
    @DisplayName("모르는 회사 코드면 ALL 만 갱신한다")
    void unknownCompanyRefreshesAllOnly() {
        enabled().refreshAfterCrawl("nosuchcompany");

        List<String> urls = capturedUrls();
        assertThat(urls).hasSize(1);
        assertThat(urls.get(0)).contains("nklcb:list:ALL");
    }

    @Test
    @DisplayName("전체 크롤 뒤에는 ALL + 회사 8개를 모두 갱신한다")
    void refreshAllCoversEveryKey() {
        enabled().refreshAll();

        List<String> urls = capturedUrls();
        assertThat(urls).hasSize(9);
        assertThat(urls).anyMatch(u -> u.contains("nklcb:list:ALL"));
        assertThat(urls).anyMatch(u -> u.contains("nklcb:list:YANOLJA"));
    }

    @Test
    @DisplayName("TTL 과 Host 헤더를 프론트/nginx 와 같은 값으로 보낸다")
    void sendsTtlAndHostHeader() {
        enabled().refreshAfterCrawl("BAEMIN");

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, org.mockito.Mockito.atLeastOnce())
            .exchange(url.capture(), eq(HttpMethod.PUT), entity.capture(), eq(String.class));

        assertThat(url.getAllValues()).allMatch(u -> u.startsWith("http://10.0.0.5/api/kv/"));
        assertThat(url.getAllValues()).allMatch(u -> u.endsWith("?ttl=21600"));

        HttpHeaders headers = entity.getValue().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.HOST)).isEqualTo("cache.example.com");
    }

    @Test
    @DisplayName("KV 오리진 미설정이면 아무것도 하지 않는다 — 로컬/미설정 환경")
    void disabledWhenOriginMissing() {
        new KvCacheRefresher(restTemplate, jobService, "", "").refreshAfterCrawl("BAEMIN");
        new KvCacheRefresher(restTemplate, jobService, "10.0.0.5", "").refreshAfterCrawl("BAEMIN");
        new KvCacheRefresher(restTemplate, jobService, null, null).refreshAll();

        verifyNoInteractions(restTemplate);
        verify(jobService, never()).listAsJson(anyString());
    }

    @Test
    @DisplayName("KV 가 죽어 있어도 크롤을 깨뜨리지 않고, 남은 키는 계속 시도한다")
    void keepsGoingWhenKvFails() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RestClientException("connection refused"));

        // 예외가 밖으로 새어나가면 크롤 엔드포인트가 통째로 실패한다.
        enabled().refreshAfterCrawl("BAEMIN");

        assertThat(capturedUrls()).hasSize(2);
    }

    @Test
    @DisplayName("목록 생성이 실패해도 나머지 키 갱신은 이어간다")
    void keepsGoingWhenListFails() {
        when(jobService.listAsJson("BAEMIN")).thenThrow(new IllegalStateException("직렬화 실패"));
        when(jobService.listAsJson("ALL")).thenReturn("[]");

        enabled().refreshAfterCrawl("BAEMIN");

        List<String> urls = capturedUrls();
        assertThat(urls).hasSize(1);
        assertThat(urls.get(0)).contains("nklcb:list:ALL");
    }
}
