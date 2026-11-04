package com.nklcbdty.api.companyfeature.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nklcbdty.api.companyfeature.dto.WorkStyleCompanyDto;
import com.nklcbdty.api.companyfeature.repository.CompanyFeatureRepository;
import com.nklcbdty.api.companyfeature.repository.WelfareCategoryRepository;
import com.nklcbdty.api.companyfeature.vo.CompanyFeature;
import com.nklcbdty.api.companyfeature.vo.FeatureType;
import com.nklcbdty.api.companyfeature.vo.WelfareCategory;
import com.nklcbdty.api.companyfeature.vo.WelfareMatch;
import com.nklcbdty.api.companyfeature.vo.WorkStyle;

class CompanyFeatureServiceTest {

    private CompanyFeatureRepository featureRepository;
    private WelfareCategoryRepository categoryRepository;
    private CompanyFeatureService service;

    @BeforeEach
    void setUp() {
        featureRepository = mock(CompanyFeatureRepository.class);
        categoryRepository = mock(WelfareCategoryRepository.class);
        service = new CompanyFeatureService(featureRepository, categoryRepository);

        when(categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(
            category("MEAL", "식대", 1),
            category("HEALTH", "건강", 2)
        ));
        when(featureRepository.findByFeatureTypeAndFeatureCode(FeatureType.WORK_STYLE, "REMOTE")).thenReturn(List.of(
            feature("LINE", FeatureType.WORK_STYLE, "REMOTE", 1L),
            feature("LINE", FeatureType.WORK_STYLE, "REMOTE", 2L),
            feature("TOSS", FeatureType.WORK_STYLE, "REMOTE", 3L)
        ));
        when(featureRepository.findByFeatureTypeAndCompanyCdIn(eq(FeatureType.WELFARE), any())).thenReturn(List.of(
            feature("LINE", FeatureType.WELFARE, "MEAL", 1L),
            feature("LINE", FeatureType.WELFARE, "HEALTH", 2L),
            feature("TOSS", FeatureType.WELFARE, "MEAL", 3L),
            // 비활성(혹은 지워진) 카테고리 근거는 응답에 나오면 안 된다
            feature("TOSS", FeatureType.WELFARE, "VACATION", 3L)
        ));
    }

    @Test
    @DisplayName("근무형태 근거가 있는 회사만, 근거 공고가 많은 순으로 나온다")
    void listsCompaniesWithWorkStyle() {
        List<WorkStyleCompanyDto> result = service.findCompanies(WorkStyle.REMOTE, null);

        assertThat(result).extracting(WorkStyleCompanyDto::getCompanyCd).containsExactly("LINE", "TOSS");
        WorkStyleCompanyDto line = result.get(0);
        assertThat(line.getCompanyNm()).isEqualTo("라인");
        assertThat(line.getPostingCount()).isEqualTo(2);
        assertThat(line.getWorkStyleLabel()).isEqualTo("재택근무");
        assertThat(line.getEvidences()).hasSize(2);
        assertThat(line.getWelfares()).extracting("code").containsExactly("MEAL", "HEALTH");
    }

    @Test
    @DisplayName("비활성 카테고리 근거는 회사 복지 목록에서 빠진다")
    void hidesInactiveCategories() {
        WorkStyleCompanyDto toss = service.findCompanies(WorkStyle.REMOTE, null).get(1);

        assertThat(toss.getWelfares()).extracting("code").containsExactly("MEAL");
    }

    @Test
    @DisplayName("복지 필터는 선택한 카테고리를 모두 가진 회사만 남긴다(AND)")
    void filtersByAllSelectedWelfares() {
        assertThat(service.findCompanies(WorkStyle.REMOTE, List.of("MEAL")))
            .extracting(WorkStyleCompanyDto::getCompanyCd).containsExactly("LINE", "TOSS");
        assertThat(service.findCompanies(WorkStyle.REMOTE, List.of("meal", "HEALTH")))
            .extracting(WorkStyleCompanyDto::getCompanyCd).containsExactly("LINE");
    }

    @Test
    @DisplayName("ANY 면 고른 복지 중 하나라도 가진 회사가 남는다")
    void filtersByAnySelectedWelfare() {
        assertThat(service.findCompanies(WorkStyle.REMOTE, List.of("MEAL", "HEALTH"), WelfareMatch.ANY))
            .extracting(WorkStyleCompanyDto::getCompanyCd).containsExactly("LINE", "TOSS");
        assertThat(service.findCompanies(WorkStyle.REMOTE, List.of("HEALTH"), WelfareMatch.ANY))
            .extracting(WorkStyleCompanyDto::getCompanyCd).containsExactly("LINE");
    }

    @Test
    @DisplayName("근무형태 없이 복지만으로 고르면 재택이 아닌 회사도 나온다")
    void filtersByWelfareWithoutWorkStyle() {
        when(featureRepository.findByFeatureType(FeatureType.WELFARE)).thenReturn(List.of(
            feature("LINE", FeatureType.WELFARE, "MEAL", 1L),
            feature("LINE", FeatureType.WELFARE, "HEALTH", 2L),
            feature("NAVER", FeatureType.WELFARE, "HEALTH", 5L),
            feature("NAVER", FeatureType.WELFARE, "HEALTH", 6L),
            // 비활성 카테고리만 가진 회사는 복지 회사로 치지 않는다
            feature("TOSS", FeatureType.WELFARE, "VACATION", 3L)
        ));

        List<WorkStyleCompanyDto> health = service.findCompanies(null, List.of("HEALTH"), WelfareMatch.ALL);
        assertThat(health).extracting(WorkStyleCompanyDto::getCompanyCd).containsExactly("NAVER", "LINE");
        WorkStyleCompanyDto naver = health.get(0);
        assertThat(naver.getWorkStyle()).isNull();
        assertThat(naver.getPostingCount()).isEqualTo(2);
        assertThat(naver.getEvidences()).isEmpty();

        assertThat(service.findCompanies(null, List.of("MEAL", "HEALTH"), WelfareMatch.ALL))
            .extracting(WorkStyleCompanyDto::getCompanyCd).containsExactly("LINE");
        assertThat(service.findCompanies(null, List.of("MEAL", "HEALTH"), WelfareMatch.ANY))
            .extracting(WorkStyleCompanyDto::getCompanyCd).containsExactlyInAnyOrder("LINE", "NAVER");
        assertThat(service.findCompanies(null, List.of(), WelfareMatch.ALL))
            .extracting(WorkStyleCompanyDto::getCompanyCd).containsExactlyInAnyOrder("LINE", "NAVER");
    }

    @Test
    @DisplayName("비활성/없는 카테고리 코드는 필터에서 무시한다")
    void ignoresUnknownWelfareCodes() {
        assertThat(service.findCompanies(WorkStyle.REMOTE, List.of("VACATION", "NOPE")))
            .extracting(WorkStyleCompanyDto::getCompanyCd).containsExactly("LINE", "TOSS");
    }

    @Test
    @DisplayName("같은 공고에서 나온 근거는 한 번만, 최대 3건까지")
    void evidencesAreDistinctAndCapped() {
        when(featureRepository.findByFeatureTypeAndFeatureCode(FeatureType.WORK_STYLE, "REMOTE")).thenReturn(List.of(
            feature("NAVER", FeatureType.WORK_STYLE, "REMOTE", 1L),
            feature("NAVER", FeatureType.WORK_STYLE, "REMOTE", 1L),
            feature("NAVER", FeatureType.WORK_STYLE, "REMOTE", 2L),
            feature("NAVER", FeatureType.WORK_STYLE, "REMOTE", 3L),
            feature("NAVER", FeatureType.WORK_STYLE, "REMOTE", 4L)
        ));

        WorkStyleCompanyDto naver = service.findCompanies(WorkStyle.REMOTE, null).get(0);

        assertThat(naver.getPostingCount()).isEqualTo(4);
        assertThat(naver.getEvidences()).extracting("jobId").doesNotHaveDuplicates().hasSize(3);
    }

    @Test
    @DisplayName("근거가 하나도 없으면 빈 목록")
    void emptyWhenNoEvidence() {
        when(featureRepository.findByFeatureTypeAndFeatureCode(FeatureType.WORK_STYLE, "REMOTE")).thenReturn(List.of());

        assertThat(service.findCompanies(WorkStyle.REMOTE, List.of("MEAL"))).isEmpty();
    }

    private static WelfareCategory category(String code, String name, int order) {
        WelfareCategory c = new WelfareCategory();
        c.setCode(code);
        c.setName(name);
        c.setSortOrder(order);
        c.setKeywords(name);
        return c;
    }

    private static CompanyFeature feature(String companyCd, FeatureType type, String code, Long jobId) {
        CompanyFeature f = new CompanyFeature();
        f.setCompanyCd(companyCd);
        f.setFeatureType(type);
        f.setFeatureCode(code);
        f.setJobId(jobId);
        f.setAnnoSubject("공고 " + jobId);
        f.setMatchedKeyword("재택");
        f.setSnippet("주 2회 재택근무");
        f.setDetectedDts(LocalDateTime.of(2026, 11, 1, 0, 0).plusMinutes(jobId));
        return f;
    }
}
