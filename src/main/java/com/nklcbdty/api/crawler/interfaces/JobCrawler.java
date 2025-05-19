package com.nklcbdty.api.crawler.interfaces;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.nklcbdty.api.crawler.vo.Job_mst;

public interface JobCrawler {
    CompletableFuture<List<Job_mst>> crawlJobs();
}
