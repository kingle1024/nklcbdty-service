package com.nklcbdty.api.jobdelete.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nklcbdty.api.jobdelete.repository.JobDeleteRequestRepository;
import com.nklcbdty.api.jobdelete.vo.JobDeleteRequest;
import com.nklcbdty.common.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;

@ExtendWith(MockitoExtension.class)
class JobDeleteRequestServiceTest {

    @Mock
    private JobDeleteRequestRepository repository;

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobDeleteRequestService service;

    @Test
    void create_새요청이면_공고스냅샷과함께_PENDING으로_저장한다() {
        Job_mst job = new Job_mst();
        job.setId(10L);
        job.setAnnoId("A-100");
        job.setAnnoSubject("백엔드 개발자");
        job.setCompanyCd("NAVER");
        job.setSysCompanyCdNm("네이버");

        when(repository.findFirstByJobIdAndStatus(10L, JobDeleteRequest.STATUS_PENDING))
            .thenReturn(Optional.empty());
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));
        when(repository.save(any(JobDeleteRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        JobDeleteRequest saved = service.create(10L, "중복 공고", null, "1.2.3.4");

        assertThat(saved.getJobId()).isEqualTo(10L);
        assertThat(saved.getStatus()).isEqualTo(JobDeleteRequest.STATUS_PENDING);
        assertThat(saved.getAnnoSubject()).isEqualTo("백엔드 개발자");
        assertThat(saved.getSysCompanyCdNm()).isEqualTo("네이버");
        verify(repository, times(1)).save(any(JobDeleteRequest.class));
    }

    @Test
    void create_이미PENDING이_있으면_중복저장하지않고_기존건반환한다() {
        JobDeleteRequest existing = new JobDeleteRequest();
        existing.setId(99L);
        existing.setJobId(10L);
        existing.setStatus(JobDeleteRequest.STATUS_PENDING);

        when(repository.findFirstByJobIdAndStatus(10L, JobDeleteRequest.STATUS_PENDING))
            .thenReturn(Optional.of(existing));

        JobDeleteRequest result = service.create(10L, null, null, null);

        assertThat(result.getId()).isEqualTo(99L);
        verify(repository, never()).save(any(JobDeleteRequest.class));
    }

    @Test
    void approve_승인하면_상태가APPROVED가되고_실제공고를_삭제한다() {
        JobDeleteRequest request = new JobDeleteRequest();
        request.setId(1L);
        request.setJobId(10L);
        request.setStatus(JobDeleteRequest.STATUS_PENDING);

        when(repository.findById(1L)).thenReturn(Optional.of(request));
        when(jobRepository.existsById(10L)).thenReturn(true);
        when(repository.save(any(JobDeleteRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        JobDeleteRequest result = service.approve(1L, "admin");

        assertThat(result.getStatus()).isEqualTo(JobDeleteRequest.STATUS_APPROVED);
        assertThat(result.getProcessedBy()).isEqualTo("admin");
        assertThat(result.getProcessDts()).isNotNull();
        verify(jobRepository, times(1)).deleteById(10L);
    }

    @Test
    void reject_반려하면_상태가REJECTED가되고_공고는_삭제하지않는다() {
        JobDeleteRequest request = new JobDeleteRequest();
        request.setId(2L);
        request.setJobId(20L);
        request.setStatus(JobDeleteRequest.STATUS_PENDING);

        when(repository.findById(2L)).thenReturn(Optional.of(request));
        when(repository.save(any(JobDeleteRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        JobDeleteRequest result = service.reject(2L, "admin");

        assertThat(result.getStatus()).isEqualTo(JobDeleteRequest.STATUS_REJECTED);
        verify(jobRepository, never()).deleteById(eq(20L));
    }
}
