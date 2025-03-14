package com.nklcbdty.api.crawler.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        if ("ALL".equals(company)) {
            return jobRepository.findAll();
        } else {
            return jobRepository.findAllByCompanyCd(company);
        }
    }
}
