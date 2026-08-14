package com.nklcbdty.api.mycalendar.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.mycalendar.vo.MyCalendarEntry;

public interface MyCalendarEntryRepository extends JpaRepository<MyCalendarEntry, Long> {

    /** 그 달에 적어 둔 내 일정. 날짜 오름차순, 같은 날은 등록순. */
    List<MyCalendarEntry> findByUserIdAndApplyDateBetweenOrderByApplyDateAscIdAsc(
        String userId, LocalDate from, LocalDate to);

    /**
     * 수정·삭제 전 소유자 확인용. id 만으로 찾으면 남의 일정을 건드릴 수 있어
     * 언제나 userId 를 함께 건다.
     */
    Optional<MyCalendarEntry> findByIdAndUserId(Long id, String userId);
}
