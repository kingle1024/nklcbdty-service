package com.nklcbdty.api.resumereview.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.Change;
import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.ChangeType;

/**
 * 모델이 제안한 변경을 원문에 하나씩 적용한다.
 *
 * <p>모델에게 고친 글 전체를 다시 쓰게 하지 않는 이유: 다시 쓴 글은 어디가 바뀌었는지 알 수 없고,
 * 설명에 없는 곳까지 슬쩍 바뀌어도 잡아낼 방법이 없다. 변경 목록을 받아 서버가 적용하면
 * 결과물의 모든 차이가 목록 안에 있고, 각 항목에 이유가 붙는다.</p>
 *
 * <p>모델이 원문을 정확히 베끼지 못하는 일이 있다(PDF 줄바꿈, 공백 차이). 그래서 정확히 일치하지 않으면
 * 공백만 느슨하게 맞춰 한 번 더 찾고, 그래도 없으면 적용하지 않고 {@code applied=false} 로 남긴다 —
 * 엉뚱한 곳을 고치는 것보다 제안으로만 보여 주는 편이 낫다.</p>
 */
public final class ChangeApplier {

    private ChangeApplier() {
    }

    public record Proposed(ChangeType type, String section, String before, String after, String reason) {
    }

    public record Result(String revisedText, List<Change> changes) {
    }

    public static Result apply(String original, List<Proposed> proposals) {
        return apply(original, proposals, null, null);
    }

    /**
     * {@code source} 가 있으면 고친 글에 원문 근거 없는 낱말이 끼어든 변경은 적용하지 않고
     * 이유에 무엇이 문제인지 덧붙인다({@link UnsupportedFactCheck}).
     */
    public static Result apply(String original, List<Proposed> proposals, String source, String posting) {
        String text = original;
        final List<Change> changes = new ArrayList<>(proposals.size());
        for (Proposed p : proposals) {
            final String before = p.before() == null ? "" : p.before().strip();
            final String after = p.after() == null ? "" : p.after().strip();
            final ChangeType type = p.type() == null ? ChangeType.REPLACE : p.type();

            String reason = p.reason();
            String next = null;
            final java.util.Set<String> unsupported = source == null ? java.util.Set.of()
                    : UnsupportedFactCheck.unsupported(after, source, posting);
            if (!unsupported.isEmpty()) {
                reason = (reason == null || reason.isBlank() ? "" : reason + " ")
                        + "※ 원문에 없는 내용(" + String.join(", ", unsupported) + ")이 들어 있어 자동으로 반영하지 않았습니다."
                        + " 실제로 한 일이라면 직접 고쳐 쓰세요.";
            } else if (type == ChangeType.REPLACE) {
                if (!before.isEmpty() && !before.equals(after)) {
                    next = replaceFirst(text, before, after);
                }
            } else if (!after.isEmpty()) {
                next = before.isEmpty() ? text.stripTrailing() + "\n" + after : insertAfterLine(text, before, after);
            }

            final boolean applied = next != null;
            if (applied) {
                text = next;
            }
            changes.add(new Change(type, p.section(), before, after, reason, applied));
        }
        return new Result(text, changes);
    }

    private static String replaceFirst(String text, String before, String after) {
        final int[] span = find(text, before);
        if (span == null) {
            return null;
        }
        return text.substring(0, span[0]) + after + text.substring(span[1]);
    }

    /** 기준 문장이 들어 있는 줄이 끝나는 곳에 새 줄로 넣는다. */
    private static String insertAfterLine(String text, String anchor, String added) {
        final int[] span = find(text, anchor);
        if (span == null) {
            return null;
        }
        int lineEnd = text.indexOf('\n', span[1]);
        if (lineEnd < 0) {
            return text + "\n" + added;
        }
        return text.substring(0, lineEnd) + "\n" + added + text.substring(lineEnd);
    }

    /**
     * 정확히 일치하는 곳, 없으면 공백·줄바꿈 차이를 무시하고 일치하는 곳.
     *
     * @return [시작, 끝) 또는 못 찾으면 null
     */
    static int[] find(String text, String needle) {
        final int exact = text.indexOf(needle);
        if (exact >= 0) {
            return new int[] {exact, exact + needle.length()};
        }
        final String[] words = needle.split("\\s+");
        if (words.length == 0) {
            return null;
        }
        final StringBuilder regex = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (regex.length() > 0) {
                regex.append("\\s*");
            }
            regex.append(Pattern.quote(word));
        }
        if (regex.length() == 0) {
            return null;
        }
        final Matcher m = Pattern.compile(regex.toString()).matcher(text);
        return m.find() ? new int[] {m.start(), m.end()} : null;
    }
}
