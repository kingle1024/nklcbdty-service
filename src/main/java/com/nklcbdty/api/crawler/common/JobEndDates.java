package com.nklcbdty.api.crawler.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * {@code job_mst.end_date} 문자열 해석 공통 로직.
 *
 * <p>마감일은 회사마다 포맷이 달라 문자열로 적재된다. 목록(/api/list)과 캘린더(/api/calendar/deadlines)가
 * 같은 규칙으로 읽도록 한 곳에 모았다.</p>
 */
@Slf4j
public final class JobEndDates {

    /** 마감일 없이 채용될 때까지 뽑는 상시채용 공고. */
    private static final String UNTIL_FILLED = "영입종료시";

    /** 크롤러가 마감일 파싱에 실패하면 "error" 문자열이 그대로 적재된다 → 손상 데이터로 간주. */
    private static final String CORRUPTED = "error";

    // 더 긴 포맷 (HH:mm:ss) 을 먼저 시도해서 정상 데이터에 불필요한 error 로그 안 남기게 한다.
    private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    private JobEndDates() {
    }

    /** 종료기간이 없는 상시채용 공고인지 여부 (종료일 null 또는 "영입종료시") */
    public static boolean isAlwaysRecruiting(String endDateStr) {
        return endDateStr == null || UNTIL_FILLED.equals(endDateStr);
    }

    /** 크롤러 파싱 실패로 손상된 값인지 여부 */
    public static boolean isCorrupted(String endDateStr) {
        return CORRUPTED.equals(endDateStr);
    }

    /** 마감일시. 파싱 실패 시 null. */
    public static LocalDateTime parse(String endDateStr) {
        if (endDateStr == null) {
            return null;
        }
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(endDateStr, formatter);
            } catch (Exception ignored) {
                // 다음 formatter 시도
            }
        }
        if (!isCorrupted(endDateStr)) {
            // "error" 는 이미 알려진 손상 데이터라 로그를 남기지 않는다.
            log.warn("endDate 파싱 실패: '{}'", endDateStr);
        }
        return null;
    }
}
