package com.nklcbdty.api.crawler.service;

import java.util.List;
import java.util.Random;

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
        List<Job_mst> items;
        if ("ALL".equals(company)) {
            items = jobRepository.findAll();
        } else {
            items = jobRepository.findAllByCompanyCd(company);
        }

        Random random = new Random();
        for (Job_mst item : items) {
            item.setId(random.nextLong());
        }

        return items;
    }
}
