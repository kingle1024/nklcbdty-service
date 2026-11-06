package com.nklcbdty.api.resumereview.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse;
import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.ChangeType;
import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.Fit;
import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.FitStatus;
import com.nklcbdty.api.resumereview.service.ReviewDocumentExtractor.ReviewDocument;

import lombok.extern.slf4j.Slf4j;

/**
 * 공고와 이력서·포트폴리오를 견주어 고칠 곳을 찾고, 원문에 적용한 결과와 바꾼 이유를 돌려준다.
 *
 * <p>입력은 저장하지 않는다. 공고·문서 본문은 이 요청 안에서만 쓰고 버린다.</p>
 */
@Slf4j
@Service
public class ResumeReviewService {

    static final int MAX_FILES = 5;
    static final int MAX_CHANGES_PER_DOCUMENT = 15;
    private static final int MAX_JOB_TEXT_CHARS = JobPostingFetcher.MAX_CHARS;

    private final JobPostingFetcher fetcher;
    private final ReviewDocumentExtractor extractor;
    private final GeminiJsonClient gemini;

    public ResumeReviewService(JobPostingFetcher fetcher, ReviewDocumentExtractor extractor, GeminiJsonClient gemini) {
        this.fetcher = fetcher;
        this.extractor = extractor;
        this.gemini = gemini;
    }

    public ResumeReviewResponse review(String jobUrl, String jobText, List<MultipartFile> files) {
        final List<MultipartFile> uploads = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (uploads.isEmpty()) {
            throw ResumeReviewException.badInput("이력서나 포트폴리오 파일을 하나 이상 올려주세요.");
        }
        if (uploads.size() > MAX_FILES) {
            throw ResumeReviewException.badInput("파일은 " + MAX_FILES + "개까지 올릴 수 있습니다.");
        }

        final String posting = jobPosting(jobUrl, jobText);
        final List<ReviewDocument> documents = uploads.stream().map(extractor::extract).toList();

        final GeminiJsonClient.Answer answer = gemini.generate(buildPrompt(posting, documents));
        return toResponse(answer, documents);
    }

    /** 붙여넣은 글이 있으면 그것을 쓴다. 사용자가 직접 준 글이 페이지를 긁은 것보다 정확하다. */
    private String jobPosting(String jobUrl, String jobText) {
        if (jobText != null && !jobText.isBlank()) {
            final String text = ReviewDocumentExtractor.normalize(jobText);
            return text.length() > MAX_JOB_TEXT_CHARS ? text.substring(0, MAX_JOB_TEXT_CHARS) : text;
        }
        if (jobUrl != null && !jobUrl.isBlank()) {
            return fetcher.fetch(jobUrl);
        }
        throw ResumeReviewException.badInput("공고 주소나 공고 내용을 넣어주세요.");
    }

    static String buildPrompt(String posting, List<ReviewDocument> documents) {
        final StringBuilder docs = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            final ReviewDocument d = documents.get(i);
            docs.append("<<<문서 ").append(i).append(": ").append(d.fileName()).append(">>>\n")
                .append(d.text()).append("\n<<<문서 ").append(i).append(" 끝>>>\n\n");
        }
        return """
                너는 한국 IT 업계 채용 담당자이자 이력서 컨설턴트다.
                아래 [채용 공고]에 지원하는 사람의 [지원자 문서](이력서·포트폴리오·경력기술서)를 공고에 맞게 다듬는다.

                반드시 지킬 것:
                1. 사실을 지어내지 마라. after 에 들어가는 모든 사실(한 일, 기술, 대상, 수치, 성과)은 지원자 문서 어딘가에 이미 적혀 있어야 한다.
                   할 수 있는 것: 같은 사실을 공고의 용어로 바꿔 말하기, 문서 안 다른 곳에 적힌 사실을 끌어와 합치기, 한 일과 결과를 또렷하게 나누기.
                   지원자가 실제로 했을 법해도 문서에 없는 일은 쓰지 않는다. 확인이 필요한 내용은 "[직접 입력: 개선율 %%]",
                   "[직접 입력: 장애 원인 분석에 참여했다면 사례]" 처럼 대괄호 자리표시로만 남긴다.
                   나쁜 예) 원문 "장애 대응 당번 참여" → "장애 원인 분석 및 반복 이슈 개선 수행" (원인 분석·개선은 문서에 없다)
                   좋은 예) 원문 "장애 대응 당번 참여" → "운영 장애 대응 당번 참여 ([직접 입력: 대응한 장애 유형·건수])"
                2. 문서에 근거가 없는 공고 요구사항은 고쳐 넣지 말고 suggestions 에 "무엇을 어떻게 보강하면 좋은지" 로 적는다.
                   공고 키워드를 맞추려고 문서에 없는 업무를 괄호로 덧붙이지 않는다.
                3. changes 의 before 는 해당 문서 원문에서 그대로 복사한 연속된 글자여야 한다. 한 줄 안에서 고르고, 고치려는 부분만 짧게 고른다.
                   요약하거나 고쳐 쓴 글을 before 에 넣으면 적용되지 않는다.
                4. type 은 "REPLACE"(before 를 after 로 교체) 또는 "INSERT"(before 줄 바로 뒤에 after 를 새 줄로 추가) 다.
                5. reason 에는 공고의 어떤 요구·키워드와 이어지는지, 왜 이렇게 바꾸면 나아지는지를 한두 문장으로 쓴다.
                6. 문서마다 효과가 큰 순서로 최대 %d개까지만 제안한다. 고칠 게 없으면 빈 배열로 둔다.
                7. 문서가 쓰인 언어를 유지한다. 설명(summary, reason, evidence, suggestions)은 한국어로 쓴다.

                아래 JSON 하나로만 답한다:
                {
                  "jobTitle": "공고의 직무명",
                  "company": "공고의 회사명",
                  "requirements": ["공고의 핵심 요구사항(자격요건·우대사항에서 중요한 것 5~10개)"],
                  "fit": [{"requirement": "요구사항", "status": "MATCHED|PARTIAL|MISSING", "evidence": "문서의 어디에서 보이는지, 또는 무엇이 부족한지"}],
                  "documents": [{"index": 0, "summary": "이 문서를 어떤 방향으로 고쳤는지 한두 문장",
                                 "changes": [{"type": "REPLACE", "section": "문서 안 위치(예: 경력 > 회사명)", "before": "원문 그대로", "after": "고친 글", "reason": "바꾼 이유"}]}],
                  "suggestions": ["지원자가 직접 보강해야 할 것"]
                }

                [채용 공고]
                %s

                [지원자 문서]
                %s
                """.formatted(MAX_CHANGES_PER_DOCUMENT, posting, docs);
    }

    ResumeReviewResponse toResponse(GeminiJsonClient.Answer answer, List<ReviewDocument> documents) {
        final JSONObject root;
        try {
            root = new JSONObject(stripFence(answer.json()));
        } catch (JSONException e) {
            log.warn("첨삭 답이 JSON 이 아님 model={} head={}", answer.model(),
                     answer.json().substring(0, Math.min(200, answer.json().length())));
            throw ResumeReviewException.unavailable("AI 답을 읽지 못했습니다. 다시 시도해주세요.");
        }

        final List<List<ChangeApplier.Proposed>> proposedByDoc = new ArrayList<>();
        final List<String> summaries = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            proposedByDoc.add(new ArrayList<>());
            summaries.add("");
        }
        final JSONArray docs = root.optJSONArray("documents");
        if (docs != null) {
            for (int i = 0; i < docs.length(); i++) {
                final JSONObject d = docs.optJSONObject(i);
                if (d == null) {
                    continue;
                }
                final int index = d.optInt("index", i);
                if (index < 0 || index >= documents.size()) {
                    continue;
                }
                summaries.set(index, d.optString("summary", "").strip());
                final JSONArray changes = d.optJSONArray("changes");
                if (changes == null) {
                    continue;
                }
                for (int c = 0; c < changes.length() && proposedByDoc.get(index).size() < MAX_CHANGES_PER_DOCUMENT; c++) {
                    final JSONObject ch = changes.optJSONObject(c);
                    if (ch != null) {
                        proposedByDoc.get(index).add(new ChangeApplier.Proposed(
                                changeType(ch.optString("type")), ch.optString("section", "").strip(),
                                ch.optString("before", ""), ch.optString("after", ""),
                                ch.optString("reason", "").strip()));
                    }
                }
            }
        }

        final List<ResumeReviewResponse.Document> outDocs = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            final ReviewDocument d = documents.get(i);
            final ChangeApplier.Result applied = ChangeApplier.apply(d.text(), proposedByDoc.get(i));
            outDocs.add(new ResumeReviewResponse.Document(
                    d.fileName(), summaries.get(i), d.text(), applied.revisedText(), applied.changes()));
        }

        return new ResumeReviewResponse(
                root.optString("jobTitle", "").strip(),
                root.optString("company", "").strip(),
                strings(root.optJSONArray("requirements")),
                fits(root.optJSONArray("fit")),
                outDocs,
                strings(root.optJSONArray("suggestions")),
                answer.model());
    }

    private static String stripFence(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.strip();
    }

    private static ChangeType changeType(String raw) {
        return "INSERT".equals(raw.strip().toUpperCase(Locale.ROOT)) ? ChangeType.INSERT : ChangeType.REPLACE;
    }

    private static List<String> strings(JSONArray array) {
        final List<String> out = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                final String s = array.optString(i, "").strip();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static List<Fit> fits(JSONArray array) {
        final List<Fit> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            final JSONObject f = array.optJSONObject(i);
            if (f == null || f.optString("requirement").isBlank()) {
                continue;
            }
            FitStatus status;
            try {
                status = FitStatus.valueOf(f.optString("status").strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                status = FitStatus.PARTIAL;
            }
            out.add(new Fit(f.optString("requirement").strip(), status, f.optString("evidence", "").strip()));
        }
        return out;
    }
}
