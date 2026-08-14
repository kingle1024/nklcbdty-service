package com.nklcbdty.api.mycalendar.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.mycalendar.dto.MyCalendarDayDto;
import com.nklcbdty.api.mycalendar.dto.MyCalendarEntryDto;
import com.nklcbdty.api.mycalendar.dto.MyCalendarEntryRequest;
import com.nklcbdty.api.mycalendar.dto.MyCalendarMonthDto;
import com.nklcbdty.api.mycalendar.exception.MyCalendarNotFoundException;
import com.nklcbdty.api.mycalendar.repository.MyCalendarEntryRepository;
import com.nklcbdty.api.mycalendar.vo.MyCalendarEntry;

/**
 * 나의 채용 캘린더. 내가 지원할 회사를 달력에 적어 두는 개인 메모다.
 *
 * <p>모든 메서드가 {@code userId} 를 첫 인자로 받고 저장소 조회에도 반드시 함께 건다.
 * 이 API 는 캐싱하지 않는다 — 사람마다 다른 데이터라 캐시가 섞이면 남의 일정이 보인다.</p>
 */
@Service
public class MyCalendarService {

    /** 엔티티 컬럼 길이와 맞춘다. 넘치면 DB 에서 잘리거나 500 이 나므로 여기서 400 으로 막는다. */
    private static final int MAX_COMPANY_NAME_LENGTH = 100;
    private static final int MAX_URL_LENGTH = 1000;
    private static final int MAX_MEMO_LENGTH = 2000;

    private final MyCalendarEntryRepository entryRepository;

    public MyCalendarService(MyCalendarEntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    /** 그 달에 적어 둔 내 일정을 날짜별로 모아 준다. */
    @Transactional(readOnly = true)
    public MyCalendarMonthDto getMonth(String userId, YearMonth yearMonth) {
        final List<MyCalendarEntry> entries =
            entryRepository.findByUserIdAndApplyDateBetweenOrderByApplyDateAscIdAsc(
                userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        // TreeMap: 날짜 오름차순. 하루 안에서는 조회 순서(등록순)를 유지한다.
        final Map<LocalDate, List<MyCalendarEntryDto>> byDate = new TreeMap<>();
        for (MyCalendarEntry entry : entries) {
            byDate.computeIfAbsent(entry.getApplyDate(), key -> new ArrayList<>())
                  .add(new MyCalendarEntryDto(entry));
        }

        final List<MyCalendarDayDto> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<MyCalendarEntryDto>> day : byDate.entrySet()) {
            days.add(new MyCalendarDayDto(day.getKey(), day.getValue()));
        }

        return new MyCalendarMonthDto(yearMonth, entries.size(), days);
    }

    @Transactional
    public MyCalendarEntryDto create(String userId, MyCalendarEntryRequest request) {
        final MyCalendarEntry entry = new MyCalendarEntry();
        entry.setUserId(userId);
        apply(entry, request);
        return new MyCalendarEntryDto(entryRepository.save(entry));
    }

    @Transactional
    public MyCalendarEntryDto update(String userId, Long entryId, MyCalendarEntryRequest request) {
        final MyCalendarEntry entry = mine(userId, entryId);
        apply(entry, request);
        return new MyCalendarEntryDto(entryRepository.save(entry));
    }

    @Transactional
    public void delete(String userId, Long entryId) {
        entryRepository.delete(mine(userId, entryId));
    }

    /** URL 에서 회사명을 추측한다. 추측할 수 없으면 null — 호출부가 사용자에게 직접 입력하게 둔다. */
    public String guessCompanyName(String url) {
        return CompanyNameGuesser.guess(url);
    }

    private MyCalendarEntry mine(String userId, Long entryId) {
        return entryRepository.findByIdAndUserId(entryId, userId)
                              .orElseThrow(MyCalendarNotFoundException::new);
    }

    /** 요청값을 검증해서 엔티티에 옮긴다. 등록·수정이 같은 규칙을 쓰도록 한 곳에 모았다. */
    private void apply(MyCalendarEntry entry, MyCalendarEntryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 내용이 비어 있습니다.");
        }
        entry.setApplyDate(parseDate(request.getApplyDate()));
        entry.setCompanyName(requireCompanyName(request.getCompanyName()));
        entry.setUrl(normalizeUrl(request.getUrl()));
        entry.setMemo(trimToNull(request.getMemo(), MAX_MEMO_LENGTH, "메모"));
    }

    private LocalDate parseDate(String applyDate) {
        if (isBlank(applyDate)) {
            throw new IllegalArgumentException("날짜를 선택해 주세요.");
        }
        try {
            return LocalDate.parse(applyDate.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. (yyyy-MM-dd)");
        }
    }

    private String requireCompanyName(String companyName) {
        if (isBlank(companyName)) {
            throw new IllegalArgumentException("회사명을 입력해 주세요.");
        }
        final String trimmed = companyName.trim();
        if (trimmed.length() > MAX_COMPANY_NAME_LENGTH) {
            throw new IllegalArgumentException("회사명은 " + MAX_COMPANY_NAME_LENGTH + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }

    /**
     * 스킴 없이 적어도(careers.kakao.com) 링크로 열리도록 https:// 를 붙여 준다.
     * javascript: 같은 위험한 스킴은 링크로 걸면 안 되므로 http/https 만 허용한다.
     */
    private String normalizeUrl(String url) {
        final String trimmed = trimToNull(url, MAX_URL_LENGTH, "URL");
        if (trimmed == null) {
            return null;
        }

        // 스킴에 점이 들어가는 일은 실제로 없으므로 점을 빼고 본다 —
        // 그래야 "careers.kakao.com:8080/jobs" 같은 포트 표기를 스킴으로 오해하지 않는다.
        final boolean hasScheme = trimmed.matches("(?is)^[a-z][a-z0-9+-]*:.*");
        if (hasScheme && !trimmed.matches("(?is)^https?://.*")) {
            throw new IllegalArgumentException("URL 은 http:// 또는 https:// 주소만 넣을 수 있습니다.");
        }

        final String normalized = hasScheme ? trimmed : "https://" + trimmed;
        if (normalized.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL 이 너무 깁니다.");
        }
        return normalized;
    }

    private String trimToNull(String value, int maxLength, String fieldName) {
        if (isBlank(value)) {
            return null;
        }
        final String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 은(는) " + maxLength + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
