package com.nklcbdty.api.search.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.nklcbdty.api.search.model.JobMstDocument;

public interface JobMstSearchRepository extends ElasticsearchRepository<JobMstDocument, String> {
}
