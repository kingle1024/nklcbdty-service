package com.nklcbdty.api.troubleshooting.dto;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 저장된 값이 한 칼럼에 이어붙어 있는 것들(태그·기술스택·참고 링크)을 화면이 쓰기 쉬운
 * 목록으로 푼다. 기록을 남기는 쪽이 사람이라 구분자 뒤 공백이 있는 것과 없는 것이 섞여 있고,
 * 링크는 {@code "PR: https://..."} 처럼 설명이 앞에 붙어 오기도 한다.
 */
public final class TroubleshootingTextParts {

    private TroubleshootingTextParts() {
    }

    /** 쉼표로 이어진 값을 자른다. 빈 조각과 중복은 버리고 적힌 순서를 지킨다 */
    public static List<String> splitByComma(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    }

    /** 한 줄에 하나씩 적힌 참고 링크를 자른다 */
    public static List<ReferenceLink> splitLinks(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\r?\\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(TroubleshootingTextParts::toLink)
            .collect(Collectors.toList());
    }

    /**
     * {@code "PR: https://github.com/..."} 를 라벨과 주소로 나눈다.
     * 주소가 없는 줄(예: {@code "머지 커밋: bee50aa"})은 라벨만 남기고 url 을 비운다 —
     * 화면에서 링크로 만들면 눌러도 갈 곳이 없기 때문이다.
     */
    private static ReferenceLink toLink(String line) {
        int at = indexOfUrl(line);
        if (at < 0) {
            return new ReferenceLink(line, null);
        }
        String url = line.substring(at).trim();
        String label = line.substring(0, at).replaceAll("[:\\-\\s]+$", "").trim();
        return new ReferenceLink(label.isEmpty() ? url : label, url);
    }

    private static int indexOfUrl(String line) {
        int https = line.indexOf("https://");
        int http = line.indexOf("http://");
        if (https < 0) {
            return http;
        }
        if (http < 0) {
            return https;
        }
        return Math.min(https, http);
    }

    /** 참고 링크 한 줄. url 이 null 이면 링크가 아니라 그냥 메모다 */
    public record ReferenceLink(String label, String url) {
    }
}
