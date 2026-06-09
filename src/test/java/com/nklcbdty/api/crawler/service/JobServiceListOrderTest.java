package com.nklcbdty.api.crawler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nklcbdty.common.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;

@ExtendWith(MockitoExtension.class)
class JobServiceListOrderTest {

    @Mock
    private JobRepository jobRepository;

    private Job_mst job(String subject, String endDate) {
        Job_mst j = new Job_mst();
        j.setAnnoSubject(subject);
        j.setSubJobCdNm("백엔드");
        j.setEndDate(endDate);
        return j;
    }

    @Test
    void list_종료기간있는공고가_상시채용보다_위에_오고_마감임박순으로_정렬된다() {
        Job_mst later = job("마감 늦은 공고", "2099-01-01 18:00:00");
        Job_mst always1 = job("상시채용 A(null)", null);
        Job_mst earlier = job("마감 빠른 공고", "2098-01-01 18:00:00");
        Job_mst always2 = job("상시채용 B(영입종료시)", "영입종료시");

        when(jobRepository.findAllByCompanyCdAndSubJobCdNmIsNotNullOrderByEndDateAsc("KAKAO"))
            .thenReturn(Arrays.asList(later, always1, earlier, always2));

        JobService service = new JobService(jobRepository);
        List<Job_mst> result = service.list("KAKAO");

        // 종료기간 있는 공고가 먼저(마감 임박순), 그 다음 상시채용
        assertThat(result).extracting(Job_mst::getAnnoSubject)
            .containsExactly("마감 빠른 공고", "마감 늦은 공고", "상시채용 A(null)", "상시채용 B(영입종료시)");
    }
}
