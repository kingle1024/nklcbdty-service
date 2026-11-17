package com.nklcbdty.api.log.geo;

/**
 * 방문 로그(visitor.ip_origin)에 저장하는 접속 출처.
 * 관리자 화면의 "국내 접속만" 필터는 DOMESTIC + INTERNAL 을 국내로 본다(INTERNAL 은 팀원의 국내 회선이다).
 */
public enum IpOrigin {
    /** 한국에 할당된 IPv4 대역(APNIC 기준) */
    DOMESTIC,
    /** 해외 대역. 구글 크롤러·해외 클라우드(AWS us-east 등) 봇이 대부분이다 */
    FOREIGN,
    /** 팀원·사무실 등 이미 알고 있는 IP(로그에 별칭으로 남는 것들) */
    INTERNAL,
    /** IPv6, 형식이 깨진 값 등 판별할 수 없는 것 */
    UNKNOWN;

    public boolean isDomestic() {
        return this == DOMESTIC || this == INTERNAL;
    }
}
