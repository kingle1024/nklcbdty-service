package com.nklcbdty.api.career.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.repository.CareerEntryRepository;

/**
 * 경력 기록 조회. 읽기 전용이다 — 기록은 로컬 스킬(save-career)이 쓴다.
 *
 * <p>페이징하지 않는다. 한 사람의 경력이라 많아야 수십 건이고, 화면이 회사별로 묶어
 * 보여주려면 어차피 전부가 필요하다.
 */
@Service
@Transactional(readOnly = true)
public class CareerEntryService {

    private final CareerEntryRepository repository;

    public CareerEntryService(CareerEntryRepository repository) {
        this.repository = repository;
    }

    public List<CareerEntryDto> findAll() {
        return repository.findAllRecentFirst().stream()
            .map(CareerEntryDto::from)
            .toList();
    }
}
