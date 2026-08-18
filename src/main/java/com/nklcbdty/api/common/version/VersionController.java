package com.nklcbdty.api.common.version;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지금 운영에 떠 있는 빌드가 무엇인지 알려준다.
 *
 * <p>왜 필요한가: GitHub Actions 가 초록이어도 CloudType 이 새 이미지를 안 가져가는 일이 있다.
 * batch 는 2026-05-25 이전 코드로 몇 달을 돌았고(`/actuator/scheduledtasks` 의 cron 이 소스와
 * 달라서 겨우 알아냈다), service 는 actuator 가 전부 401 이라 그마저도 못 본다. 그래서
 * "머지했는데 왜 그대로냐"를 매번 추측으로 판단했다. 이 엔드포인트가 그걸 끝낸다.</p>
 *
 * <p>{@code startedAt} 은 JVM 시작 시각이다. 배포 시각과 같아야 한다 — 배포했는데 이 값이
 * 며칠 전이면 컨테이너가 교체되지 않은 것이다.</p>
 *
 * <p>인증 없이 열어 둔다({@code AllowedPaths.VERSION}). 빌드 시각과 기동 시각뿐이라
 * 노출해도 되는 정보다.</p>
 */
@RestController
@RequestMapping("/api")
public class VersionController {

    private final ObjectProvider<BuildProperties> buildProperties;

    public VersionController(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping(value = "/version", produces = "application/json;charset=UTF-8")
    public Map<String, Object> version() {
        Instant startedAt = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());

        Map<String, Object> body = new LinkedHashMap<>();
        // build-info.properties 가 없으면(로컬에서 bootRun 등) null 로 둔다. 없어도 기동은 돼야 한다.
        BuildProperties build = buildProperties.getIfAvailable();
        body.put("buildTime", build == null ? null : kst(build.getTime()));
        body.put("version", build == null ? null : build.getVersion());
        body.put("startedAt", kst(startedAt));
        body.put("uptime", humanize(Duration.between(startedAt, Instant.now())));
        return body;
    }

    private String kst(Instant instant) {
        if (instant == null) {
            return null;
        }
        return ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Seoul")).toString();
    }

    private String humanize(Duration d) {
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) {
            return days + "일 " + hours + "시간 " + minutes + "분";
        }
        if (hours > 0) {
            return hours + "시간 " + minutes + "분";
        }
        return minutes + "분";
    }
}
