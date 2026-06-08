package com.nklcbdty.api.jobdelete.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.jobdelete.repository.JobDeleteRequestRepository;
import com.nklcbdty.api.jobdelete.vo.JobDeleteRequest;
import com.nklcbdty.common.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JobDeleteRequestService {

    private final JobDeleteRequestRepository repository;
    private final JobRepository jobRepository;

    public JobDeleteRequestService(JobDeleteRequestRepository repository, JobRepository jobRepository) {
        this.repository = repository;
        this.jobRepository = jobRepository;
    }

    /**
     * 삭제요청 생성. 이미 같은 공고에 대해 PENDING 요청이 있으면 새로 만들지 않고 기존 건을 반환(멱등).
     */
    @Transactional
    public JobDeleteRequest create(Long jobId, String reason, String requesterId, String requesterIp) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId 는 필수입니다.");
        }

        Optional<JobDeleteRequest> existing =
            repository.findFirstByJobIdAndStatus(jobId, JobDeleteRequest.STATUS_PENDING);
        if (existing.isPresent()) {
            return existing.get();
        }

        JobDeleteRequest request = new JobDeleteRequest();
        request.setJobId(jobId);
        request.setReason(reason);
        request.setRequesterId(requesterId);
        request.setRequesterIp(requesterIp);
        request.setStatus(JobDeleteRequest.STATUS_PENDING);

        // 공고 스냅샷 저장(공고가 삭제돼도 관리자가 식별 가능)
        jobRepository.findById(jobId).ifPresent(job -> {
            request.setAnnoId(job.getAnnoId());
            request.setAnnoSubject(job.getAnnoSubject());
            request.setCompanyCd(job.getCompanyCd());
            request.setSysCompanyCdNm(job.getSysCompanyCdNm());
        });

        return repository.save(request);
    }

    /** 프론트 버튼 상태용: PENDING 인 jobId 목록 */
    @Transactional(readOnly = true)
    public List<Long> pendingJobIds() {
        return repository.findJobIdsByStatus(JobDeleteRequest.STATUS_PENDING);
    }

    /** 관리자 목록. status 가 null 이면 전체. */
    @Transactional(readOnly = true)
    public List<JobDeleteRequest> list(String status) {
        if (status == null || status.isBlank()) {
            return repository.findAllByOrderByInsertDtsDesc();
        }
        return repository.findByStatusOrderByInsertDtsDesc(status.toUpperCase());
    }

    /** 승인: 상태 변경 + 실제 공고(job_mst) 삭제 */
    @Transactional
    public JobDeleteRequest approve(Long id, String processedBy) {
        JobDeleteRequest request = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("삭제요청을 찾을 수 없습니다. id=" + id));

        if (JobDeleteRequest.STATUS_APPROVED.equals(request.getStatus())) {
            return request; // 이미 처리됨
        }

        // 실제 공고 삭제
        if (request.getJobId() != null && jobRepository.existsById(request.getJobId())) {
            jobRepository.deleteById(request.getJobId());
            log.info("[JobDeleteRequest] 공고 삭제 완료 jobId={}", request.getJobId());
        } else {
            log.warn("[JobDeleteRequest] 삭제할 공고가 이미 없음 jobId={}", request.getJobId());
        }

        request.setStatus(JobDeleteRequest.STATUS_APPROVED);
        request.setProcessedBy(processedBy);
        request.setProcessDts(LocalDateTime.now());
        return repository.save(request);
    }

    /** 반려: 상태만 변경(공고는 유지) */
    @Transactional
    public JobDeleteRequest reject(Long id, String processedBy) {
        JobDeleteRequest request = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("삭제요청을 찾을 수 없습니다. id=" + id));

        request.setStatus(JobDeleteRequest.STATUS_REJECTED);
        request.setProcessedBy(processedBy);
        request.setProcessDts(LocalDateTime.now());
        return repository.save(request);
    }
}
