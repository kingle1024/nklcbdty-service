package com.nklcbdty.api.crawler.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklcbdty.api.common.CacheConfig;
import com.nklcbdty.api.crawler.common.JobEndDates;
import com.nklcbdty.common.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JobService {
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public JobService(JobRepository jobRepository, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code /api/list} 응답 JSON. 캐시 히트 시 Redis GET 한 번으로 끝난다.
     *
     * <p>{@link #list(String)} 결과를 객체로 캐싱하지 않고 직렬화까지 끝낸 문자열을 담는 이유는
     * {@link CacheConfig} 주석에 적어두었다. 직렬화는 컨트롤러가 쓰는 것과 같은
     * 스프링 관리 ObjectMapper 로 하므로 응답 본문은 캐시 도입 전과 동일하다.</p>
     */
    @Cacheable(cacheNames = CacheConfig.JOB_LIST, key = "#company")
    public String listAsJson(String company) {
        try {
            return objectMapper.writeValueAsString(list(company));
        } catch (JsonProcessingException e) {
            // 직렬화 실패는 설정/모델 문제이므로 조용히 넘기지 않는다.
            throw new IllegalStateException("공고 목록 JSON 직렬화 실패 company=" + company, e);
        }
    }

    public List<Job_mst> list(String company) {
        List<Job_mst> items = findByCompany(company);

        LocalDateTime now = LocalDateTime.now();

        // 크롤 시 같은 공고(annoId)가 중복 INSERT 될 수 있어, 조회 시 중복은 하나만 노출한다.
        final Set<String> seenKeys = new HashSet<>();
        List<Job_mst> result = new ArrayList<>();
        for (Job_mst item : items) {
            String endDateStr = item.getEndDate();
            boolean shouldAdd = false;

            if (JobEndDates.isAlwaysRecruiting(endDateStr)) {
                shouldAdd = true;
            } else if (JobEndDates.isCorrupted(endDateStr)) {
                // 크롤러가 파싱 실패 시 "error" 문자열을 그대로 적재 → 손상 데이터로 간주, 조용히 제외 (shouldAdd 는 false 유지)
            } else {
                LocalDateTime endDate = JobEndDates.parse(endDateStr);
                if (endDate != null && endDate.isAfter(now)) {
                    shouldAdd = true;
                }
            }

            if (shouldAdd && seenKeys.add(dedupKey(item))) {
                // 실제 DB PK 유지(삭제요청 등에서 공고 식별에 사용). 과거 랜덤 id 덮어쓰기 제거.
                result.add(item);
            }
        }

        // 종료기간이 있는 공고를 위로, 상시채용(종료일 없음/"영입종료시")은 아래로.
        // 종료기간이 있는 공고끼리는 마감 임박순(오름차순)으로 정렬한다.
        result.sort((a, b) -> {
            boolean aAlways = JobEndDates.isAlwaysRecruiting(a.getEndDate());
            boolean bAlways = JobEndDates.isAlwaysRecruiting(b.getEndDate());
            if (aAlways != bAlways) {
                return aAlways ? 1 : -1; // 상시채용은 뒤로
            }
            if (aAlways) {
                return 0; // 둘 다 상시채용이면 기존 순서 유지
            }
            LocalDateTime ad = JobEndDates.parse(a.getEndDate());
            LocalDateTime bd = JobEndDates.parse(b.getEndDate());
            if (ad == null && bd == null) {
                return 0;
            }
            if (ad == null) {
                return 1;
            }
            if (bd == null) {
                return -1;
            }
            return ad.compareTo(bd); // 마감 임박순
        });

        return result;
    }

    /**
     * 마감일이 {@code [from, to]} (양끝 포함) 안에 있는 공고를 마감 임박순으로 반환한다. 캘린더에서 쓴다.
     *
     * <p>{@link #list(String)} 와 두 가지가 다르다.
     * <ul>
     *   <li>캘린더에 찍을 날짜가 없는 공고(상시채용 · 손상 데이터 · 파싱 불가)는 제외한다.</li>
     *   <li>"이미 지난 마감"을 걸러내지 않는다. 지난 달로 이동해도 그 달에 마감된 공고를 볼 수 있어야 한다.</li>
     * </ul>
     */
    public List<Job_mst> findClosingBetween(String company, LocalDate from, LocalDate to) {
        final Set<String> seenKeys = new HashSet<>();
        final List<Job_mst> result = new ArrayList<>();

        for (Job_mst item : findByCompany(company)) {
            final String endDateStr = item.getEndDate();
            if (JobEndDates.isAlwaysRecruiting(endDateStr) || JobEndDates.isCorrupted(endDateStr)) {
                continue;
            }
            final LocalDateTime endDate = JobEndDates.parse(endDateStr);
            if (endDate == null) {
                continue;
            }
            final LocalDate endDay = endDate.toLocalDate();
            if (endDay.isBefore(from) || endDay.isAfter(to)) {
                continue;
            }
            if (seenKeys.add(dedupKey(item))) {
                result.add(item);
            }
        }

        // 마감 임박순. 같은 날 안에서도 시간순으로 보이게 한다.
        result.sort(Comparator.comparing((Job_mst item) -> JobEndDates.parse(item.getEndDate())));

        return result;
    }

    /** 회사별 공고 조회. company 가 "ALL" 이면 전체. 직무 미분류(subJobCdNm=null) 공고는 노출 대상이 아니다. */
    private List<Job_mst> findByCompany(String company) {
        if ("ALL".equals(company)) {
            return jobRepository.findAllBySubJobCdNmIsNotNull();
        }
        return jobRepository.findAllByCompanyCdAndSubJobCdNmIsNotNullOrderByEndDateAsc(company);
    }

    /**
     * 중복 제거 키. 같은 회사의 같은 공고(annoId)를 동일 건으로 본다.
     * annoId 가 없으면 회사+제목으로, 그것도 없으면 PK(id)로 폴백해 서로 다른 건이 합쳐지지 않게 한다.
     */
    private String dedupKey(Job_mst item) {
        final String company = item.getCompanyCd() != null ? item.getCompanyCd() : "";
        final String annoId = item.getAnnoId();
        if (annoId != null && !annoId.isBlank()) {
            return company + "|anno|" + annoId;
        }
        final String subject = item.getAnnoSubject();
        if (subject != null && !subject.isBlank()) {
            return company + "|subj|" + subject;
        }
        return "id|" + item.getId();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.JOB_LIST, allEntries = true)
    public void deleteByCompany(String company_cd) {
        jobRepository.deleteByCompanyCd(company_cd);
    }

    // company=ALL 캐시도 같이 틀어지므로 특정 키만 지우지 않고 전체를 비운다.
    @CacheEvict(cacheNames = CacheConfig.JOB_LIST, allEntries = true)
    public void deleteAll() {
        jobRepository.deleteAllInBatch();
    }
}
