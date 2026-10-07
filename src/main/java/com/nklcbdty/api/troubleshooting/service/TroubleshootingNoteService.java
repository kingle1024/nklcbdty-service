package com.nklcbdty.api.troubleshooting.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteDetailDto;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteRequest;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteSummaryDto;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingPageResponse;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingTextParts;
import com.nklcbdty.api.troubleshooting.repository.TroubleshootingNoteRepository;
import com.nklcbdty.api.troubleshooting.vo.TroubleshootingNote;

/**
 * 트러블슈팅 기록 조회·추가·수정·삭제.
 *
 * <p>로컬 스킬(save-troubleshooting)도 같은 표에 쓴다. 스킬은 slug 로 덮어쓰므로, 화면에서 고친
 * 기록을 스킬로 다시 저장하면 스킬 쪽 내용이 이긴다(반대도 마찬가지 — 마지막에 쓴 쪽).
 * 필수 칸은 스킬과 같게 맞춘다: 발생일·프로젝트·제목·증상·원인·해결.
 */
@Service
@Transactional(readOnly = true)
public class TroubleshootingNoteService {

    /** 필터 드롭다운에 내려보낼 태그 수. 태그가 기록마다 열 개씩 붙어 전부 주면 화면이 못 쓴다 */
    private static final int MAX_TAGS = 40;

    private static final int MAX_PAGE_SIZE = 100;

    /** '전체 복사' 가 한 번에 받아갈 수 있는 최대 건수 */
    private static final int MAX_EXPORT = 500;

    private static final int MAX_SLUG = 150;

    /** 스킬이 쓰는 slug 모양과 같게: 영문 소문자·숫자를 하이픈으로 잇는다 */
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    /** 같은 자리에 고정 경로가 있어 slug 로 쓰면 상세가 열리지 않는 이름 */
    private static final Set<String> RESERVED_SLUGS = Set.of("export");

    private static final Set<String> SEVERITIES = Set.of("low", "medium", "high", "critical");

    private final TroubleshootingNoteRepository repository;

    public TroubleshootingNoteService(TroubleshootingNoteRepository repository) {
        this.repository = repository;
    }

    public TroubleshootingPageResponse list(String keyword, String project, String severity,
                                            String tag, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(size <= 0 ? 20 : size, MAX_PAGE_SIZE);

        // 발생일이 같은 기록이 여러 건 있어서(하루에 두 개 이상 고친 날) id 로 순서를 못박는다.
        // 그러지 않으면 페이지를 넘길 때 같은 기록이 두 번 보이거나 빠질 수 있다.
        Sort sort = Sort.by(Sort.Order.desc("occurredOn"), Sort.Order.desc("id"));

        Page<TroubleshootingNote> found = repository.search(
            blankIfNull(keyword),
            blankIfNull(project),
            lowerBlankIfNull(severity),
            // 태그는 저장된 쪽에서 공백을 지워 맞추므로 조건도 공백을 지워 보낸다
            lowerBlankIfNull(tag).replace(" ", ""),
            PageRequest.of(safePage, safeSize, sort)
        );

        return TroubleshootingPageResponse.builder()
            .rows(found.getContent().stream()
                .map(TroubleshootingNoteSummaryDto::from)
                .collect(Collectors.toList()))
            .totalElements(found.getTotalElements())
            .totalPages(found.getTotalPages())
            .pageNumber(found.getNumber())
            .pageSize(found.getSize())
            .totalNotes(repository.count())
            .projects(repository.findProjectsByFrequency())
            .tags(popularTags())
            .severityCounts(severityCounts())
            .build();
    }

    /**
     * 조건에 맞는 기록을 <b>본문까지 전부</b> 준다. 목록 화면의 '전체 복사' 가 쓴다.
     *
     * <p>목록 응답(요약)에는 증상·원인·해결 본문이 없어서, 화면에서 전체를 텍스트로 만들려면
     * 건마다 상세를 다시 불러야 한다(N+1). 그 왕복을 없애려고 한 번에 내려준다.
     *
     * <p>정렬과 조건은 목록과 같다 — 화면에서 보고 있는 그대로가 복사돼야 한다. 페이징은
     * 하지 않지만 상한을 둔다. 기록이 수백 건으로 늘면 응답이 수 MB 가 되고, 그쯤이면
     * 붙여 넣어 쓰는 용도 자체가 성립하지 않는다.
     */
    public List<TroubleshootingNoteDetailDto> export(String keyword, String project,
                                                     String severity, String tag) {
        Sort sort = Sort.by(Sort.Order.desc("occurredOn"), Sort.Order.desc("id"));
        return repository.search(
                blankIfNull(keyword),
                blankIfNull(project),
                lowerBlankIfNull(severity),
                lowerBlankIfNull(tag).replace(" ", ""),
                PageRequest.of(0, MAX_EXPORT, sort)
            ).getContent().stream()
            .map(TroubleshootingNoteDetailDto::from)
            .collect(Collectors.toList());
    }

    /** 상세 1건. 없으면 빈 값을 돌려주는 대신 호출부가 404 로 판단할 수 있게 null 을 준다 */
    public TroubleshootingNoteDetailDto findBySlug(String slug) {
        return repository.findBySlug(slug)
            .map(TroubleshootingNoteDetailDto::from)
            .orElse(null);
    }

    /** 쉼표로 이어붙은 태그를 모두 풀어, 많이 쓰인 순(같으면 이름 순)으로 자른다 */
    private List<String> popularTags() {
        Map<String, Long> counts = repository.findAllTagStrings().stream()
            .flatMap(raw -> TroubleshootingTextParts.splitByComma(raw).stream())
            .map(String::toLowerCase)
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        return counts.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(e -> -e.getValue())
                .thenComparing(Map.Entry::getKey))
            .limit(MAX_TAGS)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /** 심각도 집계. 화면 순서를 고정하려고 심각한 것부터 담는다 */
    private Map<String, Long> severityCounts() {
        Map<String, Long> raw = repository.countBySeverity().stream()
            .collect(Collectors.toMap(
                row -> String.valueOf(row[0]),
                row -> ((Number) row[1]).longValue(),
                Long::sum));

        Map<String, Long> ordered = new LinkedHashMap<>();
        for (String level : List.of("critical", "high", "medium", "low")) {
            if (raw.containsKey(level)) {
                ordered.put(level, raw.remove(level));
            }
        }
        // 위 네 가지에 없는 값이 저장돼 있으면 빼먹지 않고 뒤에 붙인다
        ordered.putAll(raw);
        return ordered;
    }

    @Transactional
    public TroubleshootingNoteDetailDto create(TroubleshootingNoteRequest request) {
        requireBody(request);
        String slug = trimToNull(request.getSlug());
        if (slug == null) {
            slug = newSlug(request.getProject());
        } else {
            slug = slug.toLowerCase();
            if (slug.length() > MAX_SLUG || !SLUG.matcher(slug).matches()) {
                throw new IllegalArgumentException(
                    "slug 는 영문 소문자·숫자·하이픈으로 " + MAX_SLUG + "자 이하여야 합니다.");
            }
            // '/export' 는 같은 자리의 고정 경로라 이 slug 의 상세는 영영 열리지 않는다
            if (RESERVED_SLUGS.contains(slug)) {
                throw new IllegalArgumentException("'" + slug + "' 는 쓸 수 없는 slug 입니다.");
            }
            if (repository.findBySlug(slug).isPresent()) {
                throw new IllegalArgumentException("이미 있는 slug 입니다: " + slug);
            }
        }
        TroubleshootingNote note = TroubleshootingNote.create(slug);
        apply(note, request);
        return TroubleshootingNoteDetailDto.from(repository.save(note));
    }

    /** slug 로 찾아 고친다. slug 자체는 상세 화면 주소라 바꾸지 않는다 */
    @Transactional
    public TroubleshootingNoteDetailDto update(String slug, TroubleshootingNoteRequest request) {
        TroubleshootingNote note = repository.findBySlug(slug)
            .orElseThrow(() -> new NoSuchElementException("해당 기록을 찾을 수 없습니다."));
        requireBody(request);
        apply(note, request);
        return TroubleshootingNoteDetailDto.from(note);
    }

    @Transactional
    public void delete(String slug) {
        TroubleshootingNote note = repository.findBySlug(slug)
            .orElseThrow(() -> new NoSuchElementException("해당 기록을 찾을 수 없습니다."));
        repository.delete(note);
    }

    private static void requireBody(TroubleshootingNoteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 내용이 비어 있습니다.");
        }
    }

    /** 값을 검사하고 정리해서 넣는다. 본문은 앞뒤 공백만 걷고 손대지 않는다(에러 메시지 원문이 검색 키) */
    private void apply(TroubleshootingNote note, TroubleshootingNoteRequest r) {
        if (r.getOccurredOn() == null) {
            throw new IllegalArgumentException("발생일을 입력해 주세요.");
        }
        String severity = trimToNull(r.getSeverity());
        if (severity != null) {
            severity = severity.toLowerCase();
            if (!SEVERITIES.contains(severity)) {
                throw new IllegalArgumentException("심각도는 low·medium·high·critical 중 하나여야 합니다.");
            }
        }
        note.apply(
            r.getOccurredOn(),
            limit(required(r.getProject(), "프로젝트"), 100, "프로젝트"),
            limit(trimToNull(r.getComponent()), 200, "구성요소"),
            limit(required(r.getTitle(), "제목"), 300, "제목"),
            severity,
            limit(trimToNull(r.getErrorCode()), 50, "에러 코드"),
            required(r.getSymptom(), "증상"),
            required(r.getRootCause(), "원인"),
            required(r.getResolution(), "해결"),
            trimToNull(r.getVerification()),
            trimToNull(r.getPrevention()),
            trimToNull(r.getLesson()),
            limit(joinComma(r.getTechStack(), false), 300, "기술"),
            limit(joinComma(r.getTags(), true), 300, "태그"),
            joinLines(r.getReferenceLinks())
        );
    }

    /** 화면에서 만든 기록의 slug. 프로젝트 이름의 영문 부분 + 날짜 + 난수 (한글 이름이면 'note') */
    private String newSlug(String project) {
        String base = project == null ? "" : project.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "note";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80).replaceAll("-+$", "");
        }
        for (int i = 0; i < 5; i++) {
            String slug = base + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000));
            if (repository.findBySlug(slug).isEmpty()) {
                return slug;
            }
        }
        throw new IllegalStateException("고유키를 만들지 못했습니다. 다시 시도해 주세요.");
    }

    private static String required(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
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

    /** 쉼표로 잇는다. 태그는 필터가 소문자·공백 없는 값으로 맞추므로 kebab-case 로 바꾼다 */
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

    private static String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lowerBlankIfNull(String value) {
        return blankIfNull(value).toLowerCase();
    }
}
