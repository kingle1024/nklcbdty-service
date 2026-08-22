package com.nklcbdty.api.mycalendar.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.mycalendar.vo.MyCalendarEntry;

public interface MyCalendarEntryRepository extends JpaRepository<MyCalendarEntry, Long> {

    /**
     * 그 달에 적어 둔 내 일정. 날짜 오름차순, 같은 날은 등록순.
     *
     * <p>{@code BETWEEN} 은 {@code NULL} 에 걸리지 않으므로 상시채용(날짜 없음)은 여기서 빠진다.
     * 상시채용은 {@link #findByUserIdAndApplyDateIsNullOrderByCompletedAscIdAsc} 로 따로 읽는다.</p>
     */
    List<MyCalendarEntry> findByUserIdAndApplyDateBetweenOrderByApplyDateAscIdAsc(
        String userId, LocalDate from, LocalDate to);

    /**
     * 마감일이 없는 상시채용. 아직 안 끝낸 것이 위로 오고(completed=false 가 먼저), 그 안에서는 등록순이다 —
     * 달을 넘겨도 같은 목록이 계속 보이므로 끝낸 것이 위에 쌓이면 눈에 거슬린다.
     */
    List<MyCalendarEntry> findByUserIdAndApplyDateIsNullOrderByCompletedAscIdAsc(String userId);

    /**
     * 수정·삭제 전 소유자 확인용. id 만으로 찾으면 남의 일정을 건드릴 수 있어
     * 언제나 userId 를 함께 건다.
     */
    Optional<MyCalendarEntry> findByIdAndUserId(Long id, String userId);
}
