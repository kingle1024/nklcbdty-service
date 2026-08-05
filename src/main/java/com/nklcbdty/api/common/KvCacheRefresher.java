package com.nklcbdty.api.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.nklcbdty.api.crawler.common.CompanyEnums;
import com.nklcbdty.api.crawler.service.JobService;

import lombok.extern.slf4j.Slf4j;

/**
 * 프론트가 목록을 읽어가는 외부 KV 캐시를 크롤 직후 최신 응답으로 덮어쓴다.
 *
 * <p>목록 캐시는 3층이고 무효화 주체가 층마다 다르다.
 * <ol>
 *   <li>Redis(스프링 {@code jobList}) — {@link com.nklcbdty.api.crawler.common.CrawlerCommonService#saveAll} 의 {@code @CacheEvict}</li>
 *   <li><b>외부 KV</b> — 지금까지 무효화 경로가 아예 없어 TTL(6시간) 만료만 기다렸다</li>
 *   <li>브라우저 메모리 — 새로고침</li>
 * </ol>
 * 프론트({@code nklcbdty-ui/src/common/kvCache.ts})는 2번을 <b>먼저</b> 읽고 미스일 때만 원본 API 로
 * 폴백한다. 그래서 크롤이 새 공고를 넣어도 최대 6시간 동안 화면에 안 나왔다. 실제로 배민 공고를
 * 되살린 뒤에도 API 는 10건인데 화면은 0건인 상태가 이어졌다.</p>
 *
 * <p>지우지 않고 <b>덮어쓰는</b> 이유: KV 서버가 DELETE 를 받는지 알 수 없고(프론트는 GET/PUT 만 쓴다),
 * 지우기만 하면 다음 방문자가 원본 API 비용(예열 후에도 1.5~3초)을 물기 때문이다. 어차피 새 응답을
 * 만들어야 한다면 그대로 얹는 편이 낫다.</p>
 *
 * <p>{@code KV_ORIGIN_IP} / {@code KV_ORIGIN_HOST} 가 없으면 아무것도 하지 않는다. 프론트의
 * {@code docker/40-kv-origin.sh} 가 같은 환경변수로 {@code /kv} 프록시를 켜고 끄는 것과 같은 규칙이다.</p>
 */
@Component
@Slf4j
public class KvCacheRefresher {

    /** 프론트 kvCache.ts 의 키 규칙과 반드시 같아야 한다. */
    private static final String KEY_PREFIX = "nklcb:list:";
    private static final String ALL = "ALL";

    /** 프론트가 쓰는 TTL(6시간)과 맞춘다. 어긋나면 한쪽만 먼저 만료돼 동작이 헷갈린다. */
    private static final long TTL_SECONDS = 6 * 60 * 60;

    private final RestTemplate restTemplate;
    private final JobService jobService;
    private final String originIp;
    private final String originHost;

    public KvCacheRefresher(
        RestTemplate restTemplate,
        JobService jobService,
        @Value("${kv.origin.ip:}") String originIp,
        @Value("${kv.origin.host:}") String originHost) {
        this.restTemplate = restTemplate;
        this.jobService = jobService;
        this.originIp = originIp == null ? "" : originIp.trim();
        this.originHost = originHost == null ? "" : originHost.trim();
    }

    private boolean isEnabled() {
        return !originIp.isEmpty() && !originHost.isEmpty();
    }

    /**
     * 한 회사를 크롤한 뒤 호출한다. 그 회사 키와 전체 목록 키만 갱신한다.
     *
     * <p>크롤은 해당 회사 행만 건드리므로 다른 회사 키는 여전히 유효하다. 9개를 전부 다시 만들면
     * 회사당 1.5~3초씩 크롤 응답이 늘어나는데, 배치가 회사별로 7번 호출하는 구조라 그대로 낭비가 된다.</p>
     */
    public void refreshAfterCrawl(String companyCd) {
        if (!isEnabled()) {
            log.debug("KV 오리진 미설정 — 갱신 건너뜀 (TTL 만료까지 stale)");
            return;
        }
        Set<String> targets = new LinkedHashSet<>();
        String normalized = normalize(companyCd);
        if (normalized != null) {
            targets.add(normalized);
        }
        targets.add(ALL);
        refresh(targets);
    }

    /** {@code /api/crawler?company=all} 처럼 전 회사가 바뀐 뒤 호출한다. */
    public void refreshAll() {
        if (!isEnabled()) {
            log.debug("KV 오리진 미설정 — 갱신 건너뜀 (TTL 만료까지 stale)");
            return;
        }
        Set<String> targets = new LinkedHashSet<>();
        targets.add(ALL);
        for (CompanyEnums company : CompanyEnums.values()) {
            targets.add(company.getCompanyCd());
        }
        refresh(targets);
    }

    /**
     * 컨트롤러 파라미터("baemin")를 KV 키("BAEMIN")로 맞춘다.
     * enum 에 없는 값이면 null 을 주고 전체 키만 갱신하게 한다.
     */
    private String normalize(String companyCd) {
        CompanyEnums company = CompanyEnums.fromCompanyCd(companyCd);
        return company == null ? null : company.getCompanyCd();
    }

    private void refresh(Set<String> companyCds) {
        List<String> failed = new ArrayList<>();

        for (String companyCd : companyCds) {
            try {
                // saveAll 의 @CacheEvict 가 끝난 뒤에 불려야 최신 값이 나온다.
                // (@CacheEvict 는 기본이 beforeInvocation=false 라 메서드 반환 후 비워진다)
                String json = jobService.listAsJson(companyCd);
                put(companyCd, json);
            } catch (Exception e) {
                // KV 갱신 실패가 크롤을 깨서는 안 된다. 실패해도 TTL 이 걷어간다.
                failed.add(companyCd);
                log.warn("KV 갱신 실패 company={} - {}", companyCd, e.getMessage());
            }
        }

        if (failed.isEmpty()) {
            log.info("KV 목록 캐시 갱신 완료 — {}", companyCds);
        } else {
            log.warn("KV 목록 캐시 일부 갱신 실패 — 대상={} 실패={}", companyCds, failed);
        }
    }

    private void put(String companyCd, String json) {
        String url = "http://" + originIp + "/api/kv/" + KEY_PREFIX + companyCd + "?ttl=" + TTL_SECONDS;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 오리진이 이름 기반 가상호스트라 Host 헤더가 없으면 404 가 난다.
        // (프론트 nginx 의 proxy_set_header Host 와 같은 이유)
        headers.set(HttpHeaders.HOST, originHost);

        HttpEntity<byte[]> request = new HttpEntity<>(json.getBytes(StandardCharsets.UTF_8), headers);
        restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
    }
}
