package com.nklcbdty.api.crawler.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.crawler.repository.JobRepository;
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

        Random random = new Random();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        List<Job_mst> result = new ArrayList<>();
        for (Job_mst item : items) {
            String endDateStr = item.getEndDate();
            boolean shouldAdd = false;

            if (endDateStr == null) {
                shouldAdd = true;
            } else if ("영입종료시".equals(endDateStr)) {
                shouldAdd = true;
            } else {
                LocalDateTime endDate = parseDateTime(endDateStr);
                // 파싱 불가 ("error" 같은 손상 데이터) 는 EmailService.isLive 와 동일하게 제외.
                if (endDate != null && endDate.isAfter(now)) {
                    shouldAdd = true;
                }
            }

            if (shouldAdd) {
                item.setId(random.nextLong());
                result.add(item);
            }
        }

        return result;
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
