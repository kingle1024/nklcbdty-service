package com.nklcbdty.api.log.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class IpOriginClassifierTest {

    private final IpOriginClassifier classifier = new IpOriginClassifier();

    @Test
    void 리소스의_국내_대역을_읽는다() {
        assertThat(classifier.rangeCount()).isGreaterThan(500);
    }

    // 운영 visitor 테이블에서 실제로 가장 많이 찍힌 IP 들 (2026-11-17)
    @Test
    void 국내_통신사_IP_는_국내() {
        assertThat(classifier.classify("121.137.86.218")).isEqualTo(IpOrigin.DOMESTIC);
        assertThat(classifier.classify("119.197.67.240")).isEqualTo(IpOrigin.DOMESTIC);
        assertThat(classifier.classify("61.98.149.183")).isEqualTo(IpOrigin.DOMESTIC);
    }

    @Test
    void 구글_크롤러와_해외_클라우드는_해외() {
        assertThat(classifier.classify("66.249.90.130")).isEqualTo(IpOrigin.FOREIGN);
        assertThat(classifier.classify("34.229.252.179")).isEqualTo(IpOrigin.FOREIGN);
        // 서울 리전 AWS 라도 할당은 ARIN(미국) — 봇이므로 해외로 거르는 게 맞다
        assertThat(classifier.classify("3.41.210.138")).isEqualTo(IpOrigin.FOREIGN);
    }

    @Test
    void 팀원은_IP_로도_별칭으로도_내부() {
        assertThat(classifier.classify("221.133.55.105")).isEqualTo(IpOrigin.INTERNAL);
        assertThat(classifier.classify("지용_사무실")).isEqualTo(IpOrigin.INTERNAL);
        assertThat(classifier.classify("115.21.251.84")).isEqualTo(IpOrigin.INTERNAL);
        assertThat(classifier.classify("민재_집")).isEqualTo(IpOrigin.INTERNAL);
        assertThat(IpOrigin.INTERNAL.isDomestic()).isTrue();
    }

    @Test
    void 프록시를_거친_목록은_첫_IP_로_본다() {
        assertThat(classifier.classify("121.137.86.218, 10.0.0.1")).isEqualTo(IpOrigin.DOMESTIC);
        assertThat(classifier.classify("66.249.90.130, 121.137.86.218")).isEqualTo(IpOrigin.FOREIGN);
    }

    @Test
    void 판별할_수_없는_값() {
        assertThat(classifier.classify(null)).isEqualTo(IpOrigin.UNKNOWN);
        assertThat(classifier.classify(" ")).isEqualTo(IpOrigin.UNKNOWN);
        assertThat(classifier.classify("2001:db8::1")).isEqualTo(IpOrigin.UNKNOWN);
        assertThat(classifier.classify("1.2.3.256")).isEqualTo(IpOrigin.UNKNOWN);
        assertThat(classifier.classify("모르는_별칭")).isEqualTo(IpOrigin.UNKNOWN);
    }

    @Test
    void 대역의_끝은_포함하지_않는다() {
        final IpOriginClassifier small = new IpOriginClassifier(new ByteArrayInputStream(
            "# 주석\n10.0.0.0 256\n\n10.0.2.0 1\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(small.classify("10.0.0.0")).isEqualTo(IpOrigin.DOMESTIC);
        assertThat(small.classify("10.0.0.255")).isEqualTo(IpOrigin.DOMESTIC);
        assertThat(small.classify("10.0.1.0")).isEqualTo(IpOrigin.FOREIGN);
        assertThat(small.classify("10.0.2.0")).isEqualTo(IpOrigin.DOMESTIC);
        assertThat(small.classify("10.0.2.1")).isEqualTo(IpOrigin.FOREIGN);
        assertThat(small.classify("9.255.255.255")).isEqualTo(IpOrigin.FOREIGN);
    }
}
