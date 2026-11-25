package com.nklcbdty.api.career.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.nklcbdty.api.career.dto.CareerEntryRequest;

/**
 * 경력 입력값을 검사하고 저장할 모양으로 정리한 결과.
 *
 * <p>관리자 경력({@code career_entry})과 마이페이지 경력({@code my_career_entry})이 같은 요청 모양
 * ({@link CareerEntryRequest})을 받으므로 검사도 한 곳에서 한다. 잘못된 값은
 * {@link IllegalArgumentException} 으로 — 각 컨트롤러가 400 으로 바꾼다.
 *
 * <p>빈 문자열은 null 로 — 화면이 '비어 있음' 을 한 가지로 보게 한다.
 */
public record CareerInput(
    String entryType,
    String company,
    String title,
    String team,
    String role,
    LocalDate startedOn,
    LocalDate endedOn,
    String summary,
    String description,
    String achievements,
    String techStack,
    String tags,
    String referenceLinks
) {

    /** 칼럼 길이. 넘기면 DB 가 잘라내거나 오류를 내기 전에 여기서 막는다 */
    static final int MAX_TYPE = 20;
    private static final int MAX_SHORT = 100;
    private static final int MAX_TITLE = 300;
    private static final int MAX_JOINED = 300;
    /** TEXT 칼럼(64KB). utf8mb4 한 글자가 최대 4바이트라 넉넉히 만 자로 끊는다 */
    private static final int MAX_TEXT = 10_000;

    /** 영문 소문자와 하이픈만 — 스킬이 쓰는 값(company, project, certificate …)과 맞춘다 */
    private static final Pattern ENTRY_TYPE = Pattern.compile("[a-z][a-z-]*");

    public static CareerInput of(CareerEntryRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("요청 내용이 비어 있습니다.");
        }
        String entryType = trimToNull(r.getEntryType());
        if (entryType == null) {
            throw new IllegalArgumentException("종류를 선택해 주세요.");
        }
        entryType = entryType.toLowerCase();
        if (entryType.length() > MAX_TYPE || !ENTRY_TYPE.matcher(entryType).matches()) {
            throw new IllegalArgumentException("종류는 영문 소문자로 " + MAX_TYPE + "자 이하여야 합니다.");
        }
        String title = trimToNull(r.getTitle());
        if (title == null) {
            throw new IllegalArgumentException("제목을 입력해 주세요.");
        }
        if (r.getStartedOn() != null && r.getEndedOn() != null && r.getEndedOn().isBefore(r.getStartedOn())) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다.");
        }

        return new CareerInput(
            entryType,
            limit(trimToNull(r.getCompany()), MAX_SHORT, "회사명"),
            limit(title, MAX_TITLE, "제목"),
            limit(trimToNull(r.getTeam()), MAX_SHORT, "부서"),
            limit(trimToNull(r.getRole()), MAX_SHORT, "직급·역할"),
            r.getStartedOn(),
            r.getEndedOn(),
            limit(trimToNull(r.getSummary()), MAX_TEXT, "요약"),
            limit(trimToNull(r.getDescription()), MAX_TEXT, "상세 내용"),
            limit(joinLines(r.getAchievements()), MAX_TEXT, "성과"),
            limit(joinComma(r.getTechStack(), false), MAX_JOINED, "기술"),
            limit(joinComma(r.getTags(), true), MAX_JOINED, "태그"),
            limit(joinLines(r.getReferenceLinks()), MAX_TEXT, "참고 링크")
        );
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String limit(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(label + "은(는) " + max + "자 이하로 입력해 주세요.");
        }
        return value;
    }

    private static String joinLines(List<String> items) {
        if (items == null) {
            return null;
        }
        String joined = items.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining("\n"));
        return joined.isEmpty() ? null : joined;
    }

    /** 쉼표로 잇는다. 태그는 검색 필터와 맞추려고 소문자 kebab-case 로 바꾼다 */
    private static String joinComma(List<String> items, boolean kebab) {
        if (items == null) {
            return null;
        }
        String joined = items.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> kebab ? s.toLowerCase().replaceAll("\\s+", "-") : s)
            .distinct()
            .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }
}
