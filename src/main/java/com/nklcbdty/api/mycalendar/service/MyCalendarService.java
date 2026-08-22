package com.nklcbdty.api.mycalendar.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * <p>일정은 두 종류다. 마감일이 있는 일정은 그 날짜 칸에 찍히고, 마감일이 없는 상시채용은
 * {@code applyDate} 를 비운 채로 저장해 달력 아래 목록에 모인다. 상시채용은 저절로 사라지지
 * 않으므로 완료 표시({@link #setCompleted})로 끝냈다고 적을 수 있다.</p>
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

    /**
     * 그 달에 적어 둔 내 일정을 날짜별로 모아 준다. 상시채용은 특정 달에 속하지 않으므로
     * 어느 달을 조회하든 같은 목록을 함께 담아 준다.
     */
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

        return new MyCalendarMonthDto(yearMonth, entries.size(), days, ongoingEntries(userId));
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

    /**
     * 완료 표시를 켜고 끈다. 이미 같은 상태여도 그대로 성공시킨다 — 달력 칸과 상시채용 목록
     * 두 곳에서 같은 일정을 누를 수 있어 두 번 눌리는 일이 실제로 생긴다.
     *
     * @param completed {@code false} 면 완료를 되돌린다(잘못 눌렀을 때 필요하다).
     */
    @Transactional
    public MyCalendarEntryDto setCompleted(String userId, Long entryId, boolean completed) {
        final MyCalendarEntry entry = mine(userId, entryId);
        if (!completed) {
            // 되돌리면 시각도 지운다. 남겨 두면 "완료 아님 + 완료 시각 있음" 이라는 앞뒤 안 맞는 값이 된다.
            entry.setCompletedDts(null);
        } else if (!entry.isCompleted() || entry.getCompletedDts() == null) {
            // 이미 완료였으면 처음 끝낸 시각을 그대로 둔다 — 두 번 눌렀다고 "언제 지원했나" 가
            // 오늘로 밀려나면 안 된다. 시각이 비어 있는 옛 데이터만 지금으로 채운다.
            entry.setCompletedDts(LocalDateTime.now());
        }
        entry.setCompleted(completed);
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

    /** 날짜 없이 적어 둔 상시채용. 정렬(안 끝낸 것 먼저, 그 안에서 등록순)은 저장소 쿼리가 맡는다. */
    private List<MyCalendarEntryDto> ongoingEntries(String userId) {
        final List<MyCalendarEntryDto> ongoing = new ArrayList<>();
        for (MyCalendarEntry entry
                : entryRepository.findByUserIdAndApplyDateIsNullOrderByCompletedAscIdAsc(userId)) {
            ongoing.add(new MyCalendarEntryDto(entry));
        }
        return ongoing;
    }

    private MyCalendarEntry mine(String userId, Long entryId) {
        return entryRepository.findByIdAndUserId(entryId, userId)
                              .orElseThrow(MyCalendarNotFoundException::new);
    }

    /**
     * 요청값을 검증해서 엔티티에 옮긴다. 등록·수정이 같은 규칙을 쓰도록 한 곳에 모았다.
     *
     * <p>완료 여부는 일부러 건드리지 않는다 — 폼이 그 값을 들고 다니지 않아도 메모만 고쳤을 때
     * 완료가 풀리지 않아야 한다. 완료는 {@link #setCompleted} 만 바꾼다.</p>
     */
    private void apply(MyCalendarEntry entry, MyCalendarEntryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 내용이 비어 있습니다.");
        }
        entry.setApplyDate(resolveApplyDate(request));
        entry.setCompanyName(requireCompanyName(request.getCompanyName()));
        entry.setUrl(normalizeUrl(request.getUrl()));
        entry.setMemo(trimToNull(request.getMemo(), MAX_MEMO_LENGTH, "메모"));
    }

    /**
     * 상시채용이면 날짜를 비운다. 날짜 입력을 지우지 않고 상시채용으로 바꾸는 일이 흔하므로
     * 같이 온 날짜는 오류로 보지 않고 그냥 버린다.
     */
    private LocalDate resolveApplyDate(MyCalendarEntryRequest request) {
        if (Boolean.TRUE.equals(request.getOngoing())) {
            return null;
        }
        return parseDate(request.getApplyDate());
    }

    private LocalDate parseDate(String applyDate) {
        if (isBlank(applyDate)) {
            throw new IllegalArgumentException("날짜를 선택하거나 상시채용으로 저장해 주세요.");
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
