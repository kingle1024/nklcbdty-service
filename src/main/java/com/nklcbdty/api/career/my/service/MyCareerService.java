package com.nklcbdty.api.career.my.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.dto.CareerEntryRequest;
import com.nklcbdty.api.career.my.repository.MyCareerEntryRepository;
import com.nklcbdty.api.career.my.vo.MyCareerEntry;
import com.nklcbdty.api.career.service.CareerInput;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingTextParts;

/**
 * 마이페이지 경력 조회·추가·수정·삭제. 언제나 로그인한 사용자 자신의 것만 다룬다.
 *
 * <p>남의 항목 id 로 수정·삭제를 시도해도 "없음"(404)으로 답한다 — 403 으로 답하면
 * 그 id 가 존재한다는 사실이 새어 나간다.
 */
@Service
@Transactional(readOnly = true)
public class MyCareerService {

    /** 한 사람이 쌓을 수 있는 항목 수. 경력기술서로는 넉넉하고, 스크립트로 표를 채우는 것은 막는다 */
    static final int MAX_ENTRIES_PER_USER = 200;

    private static final String NOT_FOUND = "해당 경력 항목을 찾을 수 없습니다.";

    private final MyCareerEntryRepository repository;

    public MyCareerService(MyCareerEntryRepository repository) {
        this.repository = repository;
    }

    public List<CareerEntryDto> findAll(String userId) {
        return repository.findAllRecentFirst(userId).stream()
            .map(MyCareerService::toDto)
            .toList();
    }

    @Transactional
    public CareerEntryDto create(String userId, CareerEntryRequest request) {
        CareerInput input = CareerInput.of(request);
        if (repository.countByUserId(userId) >= MAX_ENTRIES_PER_USER) {
            throw new IllegalArgumentException("경력은 " + MAX_ENTRIES_PER_USER + "건까지 저장할 수 있습니다.");
        }
        MyCareerEntry entry = MyCareerEntry.create(userId);
        entry.apply(input);
        return toDto(repository.save(entry));
    }

    @Transactional
    public CareerEntryDto update(String userId, Long id, CareerEntryRequest request) {
        CareerInput input = CareerInput.of(request);
        MyCareerEntry entry = repository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
        entry.apply(input);
        return toDto(repository.saveAndFlush(entry));
    }

    @Transactional
    public void delete(String userId, Long id) {
        MyCareerEntry entry = repository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
        repository.delete(entry);
    }

    /** 관리자 경력과 같은 응답 모양 — 화면이 묶기·기간 표시를 그대로 같이 쓴다. slug 는 없다 */
    static CareerEntryDto toDto(MyCareerEntry e) {
        return CareerEntryDto.builder()
            .id(e.getId())
            .entryType(e.getEntryType())
            .company(e.getCompany())
            .title(e.getTitle())
            .team(e.getTeam())
            .role(e.getRole())
            .startedOn(e.getStartedOn())
            .endedOn(e.getEndedOn())
            .summary(e.getSummary())
            .description(e.getDescription())
            .achievements(CareerEntryDto.splitLines(e.getAchievements()))
            .techStack(TroubleshootingTextParts.splitByComma(e.getTechStack()))
            .tags(TroubleshootingTextParts.splitByComma(e.getTags()))
            .referenceLinks(TroubleshootingTextParts.splitLinks(e.getReferenceLinks()))
            .updatedAt(e.getUpdatedAt())
            .build();
    }
}
