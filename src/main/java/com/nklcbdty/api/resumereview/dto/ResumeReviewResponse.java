package com.nklcbdty.api.resumereview.dto;

import java.util.List;

/**
 * 공고 대비 이력서·포트폴리오 첨삭 결과.
 *
 * <p>고친 본문({@link Document#revisedText})은 모델이 통째로 다시 쓴 글이 아니다. 모델은 "원문의 이 부분을
 * 이렇게 바꾼다" 는 변경 목록만 주고, 서버가 원문에 하나씩 적용해서 만든다. 그래서 바뀐 곳은 모두
 * {@link Document#changes} 에 이유와 함께 남고, 목록에 없는 곳은 원문 그대로다.</p>
 */
public record ResumeReviewResponse(
        /** 공고에서 읽은 직무명. 못 읽으면 빈 문자열 */
        String jobTitle,
        /** 공고에서 읽은 회사명. 못 읽으면 빈 문자열 */
        String company,
        /** 공고가 요구하는 핵심 역량·자격 */
        List<String> requirements,
        /** 요구사항별로 지금 문서가 얼마나 보여 주는지 */
        List<Fit> fit,
        List<Document> documents,
        /** 사실을 지어낼 수 없어 지원자가 직접 채워야 하는 것 */
        List<String> suggestions,
        /** 답을 만든 모델. 문제를 추적할 때 본다 */
        String model) {

    public enum FitStatus { MATCHED, PARTIAL, MISSING }

    public record Fit(String requirement, FitStatus status, String evidence) {
    }

    public record Document(
            String fileName,
            /** 이 문서를 어떤 방향으로 고쳤는지 한두 문장 */
            String summary,
            String originalText,
            String revisedText,
            List<Change> changes) {
    }

    public enum ChangeType {
        /** before 를 after 로 바꾼다 */
        REPLACE,
        /** before(기준 문장) 바로 뒤에 after 를 새 줄로 넣는다. before 가 비면 문서 끝에 붙인다 */
        INSERT
    }

    public record Change(
            ChangeType type,
            /** 문서 안 위치. 예) "경력 > ○○회사" */
            String section,
            String before,
            String after,
            /** 왜 바꿨는지 — 공고의 어떤 요구와 이어지는지 */
            String reason,
            /** 원문에서 before 를 찾아 실제로 반영했는지. false 면 제안으로만 보여 준다 */
            boolean applied) {
    }
}
