package com.nklcbdty.api.crawler.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.crawler.repository.JobRepository;
import com.nklcbdty.api.crawler.vo.Job_mst;

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
                LocalDateTime endDate = LocalDateTime.parse(endDateStr, formatter);
                if (endDate.isAfter(now)) {
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
}
