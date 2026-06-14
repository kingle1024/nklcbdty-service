package com.nklcbdty.api.crawler.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.common.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JobService {
    private final JobRepository jobRepository;

    @Autowired
    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public List<Job_mst> list(String company) {
        List<Job_mst> items;
        if ("ALL".equals(company)) {
            items = jobRepository.findAllBySubJobCdNmIsNotNull();
        } else {
            items = jobRepository.findAllByCompanyCdAndSubJobCdNmIsNotNullOrderByEndDateAsc(company);
        }

        LocalDateTime now = LocalDateTime.now();

        // 크롤 시 같은 공고(annoId)가 중복 INSERT 될 수 있어, 조회 시 중복은 하나만 노출한다.
        final Set<String> seenKeys = new HashSet<>();
        List<Job_mst> result = new ArrayList<>();
        for (Job_mst item : items) {
            String endDateStr = item.getEndDate();
            boolean shouldAdd = false;

            if (endDateStr == null) {
                shouldAdd = true;
            } else if ("영입종료시".equals(endDateStr)) {
                shouldAdd = true;
            } else if ("error".equals(endDateStr)) {
                // 크롤러가 파싱 실패 시 "error" 문자열을 그대로 적재 → 손상 데이터로 간주, 조용히 제외 (shouldAdd 는 false 유지)
            } else {
                LocalDateTime endDate = parseDateTime(endDateStr);
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
            boolean aAlways = isAlwaysRecruiting(a.getEndDate());
            boolean bAlways = isAlwaysRecruiting(b.getEndDate());
            if (aAlways != bAlways) {
                return aAlways ? 1 : -1; // 상시채용은 뒤로
            }
            if (aAlways) {
                return 0; // 둘 다 상시채용이면 기존 순서 유지
            }
            LocalDateTime ad = parseDateTime(a.getEndDate());
            LocalDateTime bd = parseDateTime(b.getEndDate());
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

    /** 종료기간이 없는 상시채용 공고인지 여부 (종료일 null 또는 "영입종료시") */
    private boolean isAlwaysRecruiting(String endDateStr) {
        return endDateStr == null || "영입종료시".equals(endDateStr);
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
    public void deleteByCompany(String company_cd) {
        jobRepository.deleteByCompanyCd(company_cd);
    }

    public void deleteAll() {
        jobRepository.deleteAllInBatch();
    }

    // 더 긴 포맷 (HH:mm:ss) 을 먼저 시도해서 정상 데이터에 불필요한 error 로그 안 남기게 한다.
    private final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    private LocalDateTime parseDateTime(String dateTimeStr) {
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (Exception ignored) {
                // 다음 formatter 시도
            }
        }
        log.warn("endDate 파싱 실패: '{}'", dateTimeStr);
        return null;
    }
}
