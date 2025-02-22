package com.nklcbdty.api.crawler.interfaces;

import java.util.List;

import com.nklcbdty.api.crawler.vo.Job_mst;

public interface JobCrawler {
    List<Job_mst> crawlJobs();
}
