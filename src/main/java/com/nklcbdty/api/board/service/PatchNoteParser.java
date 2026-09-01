package com.nklcbdty.api.board.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * patch-notes.md 를 {@link PatchNote} 목록으로 읽는다.
 *
 * <p>항목 머리글은 {@code ## yyyy-MM-dd | 제목} 이고, 다음 머리글 전까지가 본문이다. 파일 맨 위의
 * 설명(작성 규칙)은 첫 머리글 앞에 있으므로 자연히 버려진다.</p>
 *
 * <p>형식이 어긋난 줄에서 예외를 던지지 않는다. 패치노트 한 줄 때문에 기동이 실패하면 안 되므로,
 * 문제가 있는 항목만 건너뛰고 로그로 남긴다.</p>
 */
@Slf4j
public final class PatchNoteParser {

    private static final String HEADING_MARK = "## ";
    private static final String KEY_PREFIX = "[패치노트] ";

    private PatchNoteParser() {
    }

    /** 파일에 적힌 순서(위 → 아래, 오래된 것 → 최신)대로 돌려준다. */
    public static List<PatchNote> parse(String markdown) {
        List<PatchNote> notes = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return notes;
        }

        // 같은 날짜가 여러 번 나오면 (2), (3) ... 을 붙여 열쇠를 구분한다.
        Map<LocalDate, Integer> seenPerDate = new HashMap<>();

        LocalDate date = null;
        String title = "";
        StringBuilder body = new StringBuilder();

        for (String line : markdown.lines().toList()) {
            Heading heading = heading(line);
            if (heading == null) {
                // 항목이 시작되기 전(파일 상단 설명)이나, 날짜가 아닌 머리글은 본문으로 흘린다.
                if (date != null) {
                    body.append(line).append('\n');
                }
                continue;
            }

            addIfPublishable(notes, date, title, body, seenPerDate);
            date = heading.date();
            title = heading.title();
            body = new StringBuilder();
        }
        addIfPublishable(notes, date, title, body, seenPerDate);
        return notes;
    }

    private record Heading(LocalDate date, String title) {
    }

    /** 항목 머리글이면 날짜와 제목, 아니면 null. {@code ### } 같은 하위 머리글은 항목이 아니다. */
    private static Heading heading(String line) {
        String trimmed = line.strip();
        if (!trimmed.startsWith(HEADING_MARK)) {
            return null;
        }
        String heading = trimmed.substring(HEADING_MARK.length()).strip();
        int separator = heading.indexOf('|');
        String dateToken = (separator < 0 ? heading : heading.substring(0, separator)).strip();
        String title = separator < 0 ? "" : heading.substring(separator + 1).strip();

        LocalDate date = parseDate(dateToken);
        return date == null ? null : new Heading(date, title);
    }

    private static void addIfPublishable(List<PatchNote> notes, LocalDate date, String title,
                                         StringBuilder body, Map<LocalDate, Integer> seenPerDate) {
        if (date == null) {
            return;
        }
        String content = body.toString().strip();
        if (content.isEmpty()) {
            log.warn("[PatchNote] 본문이 비어 공지로 올리지 않는다 date={} title={}", date, title);
            return;
        }
        int ordinal = seenPerDate.merge(date, 1, Integer::sum);
        String key = ordinal == 1 ? KEY_PREFIX + date : KEY_PREFIX + date + " (" + ordinal + ")";
        notes.add(new PatchNote(date, title, content, key));
    }

    private static LocalDate parseDate(String token) {
        try {
            return LocalDate.parse(token);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
