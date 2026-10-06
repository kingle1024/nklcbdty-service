package com.nklcbdty.api.career.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.dto.CareerEntryRequest;
import com.nklcbdty.api.career.repository.CareerEntryRepository;
import com.nklcbdty.api.career.vo.CareerEntry;

/**
 * 경력 기록 조회·추가·수정·삭제.
 *
 * <p>페이징하지 않는다. 한 사람의 경력이라 많아야 수십 건이고, 화면이 회사별로 묶어
 * 보여주려면 어차피 전부가 필요하다.
 *
 * <p>로컬 save-career 스킬도 같은 표에 쓴다. 스킬은 slug 로 덮어쓰므로, 화면에서 고친
 * 항목을 스킬로 다시 저장하면 스킬 쪽 내용이 이긴다(반대도 마찬가지 — 마지막에 쓴 쪽).
 */
@Service
@Transactional(readOnly = true)
public class CareerEntryService {

    /** 칼럼 길이. 넘기면 DB 가 잘라내거나 오류를 내기 전에 여기서 막는다 */
    private static final int MAX_TYPE = 20;
    private static final int MAX_SHORT = 100;
    private static final int MAX_TITLE = 300;
    private static final int MAX_JOINED = 300;

    /** 영문 소문자와 하이픈만 — 스킬이 쓰는 값(company, project, certificate …)과 맞춘다 */
    private static final Pattern ENTRY_TYPE = Pattern.compile("[a-z][a-z-]*");

    private static final DateTimeFormatter SLUG_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CareerEntryRepository repository;

    public CareerEntryService(CareerEntryRepository repository) {
        this.repository = repository;
    }

    public List<CareerEntryDto> findAll() {
        return repository.findAllRecentFirst().stream()
            .map(CareerEntryDto::from)
            .toList();
    }

    @Transactional
    public CareerEntryDto create(CareerEntryRequest request) {
        CareerEntry entry = CareerEntry.create(newSlug(request));
        apply(entry, request);
        return CareerEntryDto.from(repository.save(entry));
    }

    @Transactional
    public CareerEntryDto update(Long id, CareerEntryRequest request) {
        CareerEntry entry = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("해당 경력 항목을 찾을 수 없습니다."));
        apply(entry, request);
        return CareerEntryDto.from(entry);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("해당 경력 항목을 찾을 수 없습니다.");
        }
        repository.deleteById(id);
    }

    /** 값을 검사하고 정리해서 엔티티에 넣는다. 빈 문자열은 null 로 — 화면이 '비어 있음' 을 한 가지로 보게 */
    private void apply(CareerEntry entry, CareerEntryRequest r) {
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

        entry.apply(
            entryType,
            limit(trimToNull(r.getCompany()), MAX_SHORT, "회사명"),
            limit(title, MAX_TITLE, "제목"),
            limit(trimToNull(r.getTeam()), MAX_SHORT, "부서"),
            limit(trimToNull(r.getRole()), MAX_SHORT, "직급·역할"),
            r.getStartedOn(),
            r.getEndedOn(),
            trimToNull(r.getSummary()),
            trimToNull(r.getDescription()),
            joinLines(r.getAchievements()),
            limit(joinComma(r.getTechStack(), false), MAX_JOINED, "기술"),
            limit(joinComma(r.getTags(), true), MAX_JOINED, "태그"),
            joinLines(r.getReferenceLinks())
        );
    }

    /** 화면에서 만든 항목의 slug. 스킬이 쓰는 이름과 겹치지 않게 종류 + 시각 + 난수로 만든다 */
    private String newSlug(CareerEntryRequest request) {
        String type = request == null ? null : trimToNull(request.getEntryType());
        String prefix = type == null ? "entry" : type.toLowerCase().replaceAll("[^a-z-]", "");
        if (prefix.isEmpty() || prefix.length() > MAX_TYPE) {
            prefix = "entry";
        }
        for (int i = 0; i < 5; i++) {
            String slug = prefix + "-" + LocalDateTime.now().format(SLUG_TIME)
                + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000));
            if (!repository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new IllegalStateException("고유키를 만들지 못했습니다. 다시 시도해 주세요.");
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
