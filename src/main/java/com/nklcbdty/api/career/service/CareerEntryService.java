package com.nklcbdty.api.career.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

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

    /** 값을 검사하고 정리해서 엔티티에 넣는다. 검사 규칙은 마이페이지 경력과 같다({@link CareerInput}) */
    private void apply(CareerEntry entry, CareerEntryRequest request) {
        CareerInput in = CareerInput.of(request);
        entry.apply(in.entryType(), in.company(), in.title(), in.team(), in.role(),
            in.startedOn(), in.endedOn(), in.summary(), in.description(),
            in.achievements(), in.techStack(), in.tags(), in.referenceLinks());
    }

    /** 화면에서 만든 항목의 slug. 스킬이 쓰는 이름과 겹치지 않게 종류 + 시각 + 난수로 만든다 */
    private String newSlug(CareerEntryRequest request) {
        String type = request == null ? null : CareerInput.trimToNull(request.getEntryType());
        String prefix = type == null ? "entry" : type.toLowerCase().replaceAll("[^a-z-]", "");
        if (prefix.isEmpty() || prefix.length() > CareerInput.MAX_TYPE) {
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
}
