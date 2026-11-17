package com.nklcbdty.api.log.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.nklcbdty.api.log.geo.IpOrigin;
import com.nklcbdty.api.log.geo.IpOriginClassifier;

import lombok.extern.slf4j.Slf4j;

/**
 * 방문 로그(visitor)에 접속 출처 컬럼(ip_origin)을 붙이고, 비어 있는 행을 채운다.
 *
 * <p>ddl-auto=none 이라 JPA 가 컬럼을 만들지 않는다. visitor 테이블은 이미 운영에 있으므로
 * ADD COLUMN IF NOT EXISTS 로 빠진 것만 더한다. 백필은 IP 별로 한 번씩 판별해 IN 절로 묶어 갱신한다
 * (2026-11 기준 행 1만 건, 서로 다른 IP 1,500개 남짓).
 *
 * <p>여기서 실패해도 앱 기동은 막지 않는다 — 기동이 죽으면 CloudType 은 옛 컨테이너를 계속 띄워 둔다.
 */
@Slf4j
@Component
public class VisitorSchemaInitializer implements ApplicationRunner {

    private static final int CHUNK = 500;

    private final JdbcTemplate jdbcTemplate;
    private final IpOriginClassifier classifier;

    public VisitorSchemaInitializer(JdbcTemplate jdbcTemplate, IpOriginClassifier classifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.classifier = classifier;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE visitor ADD COLUMN IF NOT EXISTS ip_origin VARCHAR(16) NULL");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_visitor_insert_dts ON visitor (insert_dts)");
            backfill();
        } catch (Exception e) {
            log.error("visitor.ip_origin 준비 실패 — 관리자 접속 로그의 국내 필터가 비어 보일 수 있다", e);
        }
    }

    void backfill() {
        final List<String> ips = jdbcTemplate.queryForList(
            "SELECT DISTINCT insert_ip FROM visitor WHERE ip_origin IS NULL AND insert_ip IS NOT NULL", String.class);
        if (ips.isEmpty()) {
            return;
        }
        final Map<IpOrigin, List<String>> grouped = new EnumMap<>(IpOrigin.class);
        for (String ip : ips) {
            grouped.computeIfAbsent(classifier.classify(ip), k -> new ArrayList<>()).add(ip);
        }
        int updated = 0;
        for (Map.Entry<IpOrigin, List<String>> entry : grouped.entrySet()) {
            final List<String> group = entry.getValue();
            for (int i = 0; i < group.size(); i += CHUNK) {
                final List<String> chunk = group.subList(i, Math.min(i + CHUNK, group.size()));
                final String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
                final Object[] params = new Object[chunk.size() + 1];
                params[0] = entry.getKey().name();
                for (int j = 0; j < chunk.size(); j++) {
                    params[j + 1] = chunk.get(j);
                }
                updated += jdbcTemplate.update(
                    "UPDATE visitor SET ip_origin = ? WHERE ip_origin IS NULL AND insert_ip IN (" + placeholders + ")",
                    params);
            }
        }
        log.info("visitor.ip_origin 백필: IP {}개, 행 {}건", ips.size(), updated);
    }
}
