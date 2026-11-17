package com.nklcbdty.api.log.geo;

import java.util.Map;

/**
 * 팀원·사무실처럼 이미 알고 있는 접속 IP 와 그 별칭.
 * 방문 로그에는 IP 대신 별칭이 남는다(예: "지용_사무실"). 원래 LogService 의 switch 문에 있던 목록이다.
 */
public final class InternalVisitors {

    private static final Map<String, String> LABEL_BY_IP = Map.of(
        "59.30.128.202", "지용_춘천방",
        "221.133.55.105", "지용_사무실",
        "210.90.19.117", "민재_집",
        "175.210.195.224", "민재_3",
        "221.151.13.104", "청년동"
    );

    private static final String MINJAE_HOME_PREFIX = "115.21.251.";

    private InternalVisitors() {
    }

    /** 알고 있는 IP 면 별칭, 아니면 IP 그대로 돌려준다. */
    public static String labelOf(String ip) {
        if (ip == null) {
            return null;
        }
        final String label = LABEL_BY_IP.get(ip);
        if (label != null) {
            return label;
        }
        if (ip.contains(MINJAE_HOME_PREFIX)) {
            return "민재_집";
        }
        return ip;
    }

    /** IP 이거나 이미 별칭으로 바뀐 값이거나, 팀원 접속이면 true. */
    public static boolean isInternal(String ipOrLabel) {
        if (ipOrLabel == null) {
            return false;
        }
        return LABEL_BY_IP.containsKey(ipOrLabel)
            || LABEL_BY_IP.containsValue(ipOrLabel)
            || ipOrLabel.contains(MINJAE_HOME_PREFIX);
    }
}
