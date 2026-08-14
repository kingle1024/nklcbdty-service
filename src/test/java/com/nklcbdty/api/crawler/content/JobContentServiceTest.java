package com.nklcbdty.api.crawler.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.nklcbdty.common.vo.Job_mst;

class JobContentServiceTest {

    @Mock
    private JobContentRepository repository;

    @Captor
    private ArgumentCaptor<JobContent> savedCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(repository.save(any(JobContent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Job_mst job() {
        Job_mst job = new Job_mst();
        job.setId(1L);
        job.setCompanyCd("BAEMIN");
        job.setAnnoId("25163");
        job.setJobDetailLink("https://career.woowahan.com/recruitment/R2605009/detail");
        return job;
    }

    /** 테스트용 수집기. supports/결과를 원하는 대로 만든다. */
    private JobContentFetcher fetcher(String name, boolean supports, JobContentFetcher.Fetched result, RuntimeException error) {
        return new JobContentFetcher() {
            @Override
            public boolean supports(Job_mst j) {
                return supports;
            }

            @Override
            public Fetched fetch(Job_mst j) {
                if (error != null) {
                    throw error;
                }
                return result;
            }

            @Override
            public String sourceName() {
                return name;
            }
        };
    }

    private String longBody() {
        return "주요업무\n" + "백엔드 서비스를 개발합니다. ".repeat(10);
    }

    @Test
    void 앞에_있는_수집기가_이긴다() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        JobContentService service = new JobContentService(repository, List.of(
            fetcher("전용", true, JobContentFetcher.Fetched.ofText(longBody()), null),
            fetcher("범용", true, JobContentFetcher.Fetched.ofText("다른 본문 " + longBody()), null)));

        assertTrue(service.collect(job()));

        org.mockito.Mockito.verify(repository).save(savedCaptor.capture());
        assertEquals("전용", savedCaptor.getValue().getSource());
    }

    @Test
    void 수집기가_예외를_던지면_실패로_기록하고_본문은_비운다() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        JobContentService service = new JobContentService(repository, List.of(
            fetcher("전용", true, null, new IllegalStateException("502 Bad Gateway"))));

        assertFalse(service.collect(job()));

        org.mockito.Mockito.verify(repository).save(savedCaptor.capture());
        JobContent saved = savedCaptor.getValue();
        assertEquals("502 Bad Gateway", saved.getFailReason());
        assertNull(saved.getContent());
        // 실패도 fetchedAt 을 남겨야 다음 주기에 같은 공고만 다시 잡히지 않는다.
        assertNotNull(saved.getFetchedAt());
    }

    @Test
    void 본문이_너무_짧으면_실패다() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        JobContentService service = new JobContentService(repository, List.of(
            fetcher("전용", true, JobContentFetcher.Fetched.ofText("#LI-DNI"), null)));

        assertFalse(service.collect(job()));

        org.mockito.Mockito.verify(repository).save(savedCaptor.capture());
        assertNull(savedCaptor.getValue().getContent());
        assertTrue(savedCaptor.getValue().getFailReason().contains("너무 짧음"));
    }

    @Test
    void 내용이_그대로면_본문을_다시_쓰지_않는다() {
        JobContentService service = new JobContentService(repository, List.of(
            fetcher("전용", true, JobContentFetcher.Fetched.ofText(longBody()), null)));

        // 먼저 한 번 수집해 해시가 박힌 row 를 만든다.
        when(repository.findById(1L)).thenReturn(Optional.empty());
        service.collect(job());
        org.mockito.Mockito.verify(repository).save(savedCaptor.capture());
        JobContent existing = savedCaptor.getValue();
        java.time.LocalDateTime firstUpdatedAt = existing.getUpdatedAt();

        // 같은 본문으로 다시 수집.
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        assertFalse(service.collect(job()), "내용이 같으면 갱신이 아니다");
        assertEquals(firstUpdatedAt, existing.getUpdatedAt(), "updatedAt 이 그대로여야 한다");
    }

    @Test
    void 지원하는_수집기가_없으면_실패로_남긴다() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        JobContentService service = new JobContentService(repository, List.of(
            fetcher("전용", false, JobContentFetcher.Fetched.ofText(longBody()), null)));

        assertFalse(service.collect(job()));

        org.mockito.Mockito.verify(repository).save(savedCaptor.capture());
        assertEquals("수집기 없음", savedCaptor.getValue().getFailReason());
    }
}
