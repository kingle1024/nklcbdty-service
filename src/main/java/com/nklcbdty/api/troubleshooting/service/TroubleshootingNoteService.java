package com.nklcbdty.api.troubleshooting.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteDetailDto;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteSummaryDto;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingPageResponse;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingTextParts;
import com.nklcbdty.api.troubleshooting.repository.TroubleshootingNoteRepository;
import com.nklcbdty.api.troubleshooting.vo.TroubleshootingNote;

/**
 * 트러블슈팅 기록 조회. 읽기 전용이다 — 기록은 로컬 스킬(save-troubleshooting)이 쓰고,
 * 이 서비스는 화면에 보여줄 뿐이라 저장·수정·삭제 경로를 두지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class TroubleshootingNoteService {

    /** 필터 드롭다운에 내려보낼 태그 수. 태그가 기록마다 열 개씩 붙어 전부 주면 화면이 못 쓴다 */
    private static final int MAX_TAGS = 40;

    private static final int MAX_PAGE_SIZE = 100;

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

    private static String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lowerBlankIfNull(String value) {
        return blankIfNull(value).toLowerCase();
    }
}
