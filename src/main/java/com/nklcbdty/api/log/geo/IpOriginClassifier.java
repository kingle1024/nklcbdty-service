package com.nklcbdty.api.log.geo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 접속 IP 가 국내(한국 할당 대역)인지 외부 API 없이 판별한다.
 *
 * <p>ip2location 조회 결과(visitor.country_name)는 응답 파싱 버그로 지금까지 한 번도 채워진 적이 없고,
 * 매 요청마다 외부 API 를 부르는 방식이라 과거 로그를 다시 판별할 수도 없다. 그래서 APNIC 이 공개하는
 * 국가별 할당 목록에서 KR IPv4 대역을 뽑아 리소스(geo/kr-ipv4.txt)로 넣어 두고 이진 탐색으로 본다.
 *
 * <p>할당 국가 기준이라 서울 리전의 AWS(3.x 등 ARIN 할당) 서버는 해외로 잡힌다. 사람이 아닌 봇을 걸러내는
 * 데는 오히려 이쪽이 맞다.
 */
@Component
public class IpOriginClassifier {

    static final String RESOURCE = "/geo/kr-ipv4.txt";

    private final long[] starts;
    private final long[] ends; // 끝은 포함하지 않는다

    public IpOriginClassifier() {
        this(IpOriginClassifier.class.getResourceAsStream(RESOURCE));
    }

    IpOriginClassifier(InputStream source) {
        if (source == null) {
            throw new IllegalStateException("국내 IP 대역 리소스가 없다: " + RESOURCE);
        }
        final List<long[]> ranges = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                final String[] parts = line.split("\\s+");
                final long start = parseIpv4(parts[0]);
                ranges.add(new long[] {start, start + Long.parseLong(parts[1])});
            }
        } catch (IOException e) {
            throw new IllegalStateException("국내 IP 대역 리소스를 읽지 못했다", e);
        }
        ranges.sort((a, b) -> Long.compare(a[0], b[0]));
        this.starts = ranges.stream().mapToLong(r -> r[0]).toArray();
        this.ends = ranges.stream().mapToLong(r -> r[1]).toArray();
    }

    /**
     * @param ipOrLabel 방문 로그의 insert_ip 값. X-Forwarded-For 처럼 "a, b" 로 올 수도 있고, 팀원이면 별칭일 수도 있다.
     */
    public IpOrigin classify(String ipOrLabel) {
        if (ipOrLabel == null || ipOrLabel.isBlank()) {
            return IpOrigin.UNKNOWN;
        }
        final String first = ipOrLabel.split(",")[0].trim();
        if (InternalVisitors.isInternal(first)) {
            return IpOrigin.INTERNAL;
        }
        final long ip = parseIpv4(first);
        if (ip < 0) {
            return IpOrigin.UNKNOWN;
        }
        return isKorean(ip) ? IpOrigin.DOMESTIC : IpOrigin.FOREIGN;
    }

    int rangeCount() {
        return starts.length;
    }

    private boolean isKorean(long ip) {
        // ip 이하에서 시작하는 마지막 대역을 찾는다
        int idx = Arrays.binarySearch(starts, ip);
        if (idx < 0) {
            idx = -idx - 2;
        }
        return idx >= 0 && ip < ends[idx];
    }

    /** IPv4 가 아니면 -1 */
    static long parseIpv4(String value) {
        final String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return -1;
        }
        long result = 0;
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit)) {
                return -1;
            }
            final int n = Integer.parseInt(octet);
            if (n > 255) {
                return -1;
            }
            result = result * 256 + n;
        }
        return result;
    }
}
