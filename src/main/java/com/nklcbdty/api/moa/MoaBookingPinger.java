package com.nklcbdty.api.moa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 모아 가계부(moa-ledger, Vercel)의 배편 자리 감시를 1분마다 깨운다.
 *
 * 모아 서버는 요청이 올 때만 도는 서버리스라 스스로 1분마다 돌 수 없고(Vercel 무료 크론은
 * 하루 한 번), 폰이 1분마다 깨어 있으면 배터리가 닳는다. 그래서 늘 떠 있는 이 서버가 매분
 * 모아의 /api/booking/tick 을 한 번 불러 준다. 확인·푸시·끄기는 전부 모아 쪽(lib/booking.ts)이
 * 하고, 여기는 시계 역할만 한다. 감시가 꺼져 있으면 모아가 바로 돌려보내므로 부담이 없다.
 *
 * moa.booking.url 을 비워 두면 아무것도 하지 않는다.
 */
@Slf4j
@Component
public class MoaBookingPinger {

    private final RestTemplate restTemplate;
    private final String tickUrl;
    private final String appKey;

    public MoaBookingPinger(
            @Value("${moa.booking.url:}") String tickUrl,
            @Value("${moa.booking.app-key:}") String appKey) {
        // 공용 RestTemplate 은 응답 5초 제한이다. 모아 쪽은 서버리스 콜드스타트에 시트까지 읽어
        // 첫 응답이 7초를 넘기도 하므로, 여기만 넉넉한 제한을 따로 둔다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(40_000);
        this.restTemplate = new RestTemplate(factory);
        this.tickUrl = tickUrl == null ? "" : tickUrl.trim();
        this.appKey = appKey == null ? "" : appKey.trim();
    }

    @Scheduled(fixedRate = 60_000L, initialDelay = 20_000L)
    public void tick() {
        if (tickUrl.isEmpty()) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (!appKey.isEmpty()) headers.set("x-app-key", appKey);
            ResponseEntity<String> res = restTemplate.exchange(
                    tickUrl, HttpMethod.POST, new HttpEntity<>(headers), String.class);
            // 매분 한 줄. 잘 도는지 콘솔 로그로 확인할 수 있어야 한다. 응답은 설정 전체가
            // 딸려 와 길므로 확인 여부·결과 한 줄(summary 또는 reason)만 남긴다.
            String body = res.getBody() == null ? "" : res.getBody();
            log.info("모아 배편 자리 감시 tick: HTTP {} checked={} {}",
                    res.getStatusCode().value(), field(body, "checked"), summaryOf(body));
        } catch (Exception e) {
            log.warn("모아 배편 자리 감시 호출 실패: {}", e.getMessage());
        }
    }

    /** JSON 을 파싱하지 않고 키 하나의 값만 꺼낸다. 로그용이라 정확할 필요는 없다. */
    private static String field(String json, String key) {
        int i = json.indexOf("\"" + key + "\":");
        if (i < 0) return "?";
        int from = i + key.length() + 3;
        int to = from;
        while (to < json.length() && ",}".indexOf(json.charAt(to)) < 0) to++;
        return json.substring(from, to).replace("\"", "");
    }

    /** "summary" 가 있으면 그것(잔여석), 없으면 "reason"(왜 안 봤는지). */
    private static String summaryOf(String json) {
        String s = field(json, "summary");
        return "?".equals(s) ? "reason=" + field(json, "reason") : s;
    }
}
