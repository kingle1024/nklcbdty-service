package com.nklcbdty.api.companyfeature.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.nklcbdty.api.companyfeature.dto.CompanyWelfareDto;
import com.nklcbdty.api.companyfeature.dto.FeatureEvidenceDto;
import com.nklcbdty.api.companyfeature.dto.WorkStyleCompanyDto;
import com.nklcbdty.api.companyfeature.repository.CompanyFeatureRepository;
import com.nklcbdty.api.companyfeature.repository.WelfareCategoryRepository;
import com.nklcbdty.api.companyfeature.vo.CompanyFeature;
import com.nklcbdty.api.companyfeature.vo.FeatureType;
import com.nklcbdty.api.companyfeature.vo.WelfareCategory;
import com.nklcbdty.api.companyfeature.vo.WorkStyle;
import com.nklcbdty.api.crawler.common.CompanyEnums;

/**
 * 근무형태(재택 등)별 회사 목록 + 복지 카테고리 필터.
 *
 * <p>회사가 그 근무형태/복지를 가졌는지는 근거 공고가 한 건 이상 있는지로 정한다(batch 가 공고 단위로 쌓는다).
 * 행 수가 회사 × 특징 × 공고 수준이라 작으므로 DB 에서 집계하지 않고 메모리에서 묶는다.</p>
 */
@Service
public class CompanyFeatureService {

    /** 회사·특징마다 화면에 보여줄 근거 공고 수 상한 */
    static final int MAX_EVIDENCES = 3;

    private final CompanyFeatureRepository featureRepository;
    private final WelfareCategoryRepository categoryRepository;

    public CompanyFeatureService(CompanyFeatureRepository featureRepository,
                                 WelfareCategoryRepository categoryRepository) {
        this.featureRepository = featureRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * @param welfareCodes 이 복지 카테고리를 <b>모두</b> 가진 회사만. 비활성/없는 코드는 무시한다
     *                     (지난 링크로 들어와도 목록이 통째로 비지 않게).
     */
    public List<WorkStyleCompanyDto> findCompanies(WorkStyle workStyle, Collection<String> welfareCodes) {
        List<CompanyFeature> styleRows =
            featureRepository.findByFeatureTypeAndFeatureCode(FeatureType.WORK_STYLE, workStyle.name());
        if (styleRows.isEmpty()) {
            return List.of();
        }
        Map<String, List<CompanyFeature>> styleByCompany = groupByCompany(styleRows);

        List<WelfareCategory> activeCategories = categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        Set<String> activeCodes = activeCategories.stream()
            .map(WelfareCategory::getCode)
            .collect(Collectors.toSet());

        Set<String> required = welfareCodes == null ? Set.of() : welfareCodes.stream()
            .filter(code -> code != null)
            .map(code -> code.trim().toUpperCase())
            .filter(activeCodes::contains)
            .collect(Collectors.toSet());

        Map<String, List<CompanyFeature>> welfareByCompany = groupByCompany(
            featureRepository.findByFeatureTypeAndCompanyCdIn(FeatureType.WELFARE, styleByCompany.keySet()));

        List<WorkStyleCompanyDto> result = new ArrayList<>();
        for (Map.Entry<String, List<CompanyFeature>> entry : styleByCompany.entrySet()) {
            String companyCd = entry.getKey();
            Map<String, List<CompanyFeature>> welfareByCode = welfareByCompany
                .getOrDefault(companyCd, List.of()).stream()
                .collect(Collectors.groupingBy(CompanyFeature::getFeatureCode));

            if (!welfareByCode.keySet().containsAll(required)) {
                continue;
            }

            List<CompanyWelfareDto> welfares = activeCategories.stream()
                .filter(c -> welfareByCode.containsKey(c.getCode()))
                .map(c -> CompanyWelfareDto.builder()
                    .code(c.getCode())
                    .name(c.getName())
                    .evidences(toEvidences(welfareByCode.get(c.getCode())))
                    .build())
                .collect(Collectors.toList());

            CompanyEnums company = CompanyEnums.fromCompanyCd(companyCd);
            List<CompanyFeature> styleEvidence = entry.getValue();
            result.add(WorkStyleCompanyDto.builder()
                .companyCd(companyCd)
                .companyNm(company == null ? companyCd : company.getCompanyNm())
                .careerPageUrl(company == null ? null : company.getCareerPageUrl())
                .workStyle(workStyle.name())
                .workStyleLabel(workStyle.getLabel())
                .postingCount((int) styleEvidence.stream().map(CompanyFeature::getJobId).distinct().count())
                .evidences(toEvidences(styleEvidence))
                .welfares(welfares)
                .build());
        }

        // 근거 공고가 많은 회사가 위로. 같으면 회사 enum 순서(목록 화면 탭 순서)
        result.sort(Comparator.comparingInt(WorkStyleCompanyDto::getPostingCount).reversed()
            .thenComparingInt(dto -> companyOrder(dto.getCompanyCd())));
        return result;
    }

    private static Map<String, List<CompanyFeature>> groupByCompany(List<CompanyFeature> rows) {
        return rows.stream().collect(Collectors.groupingBy(
            f -> f.getCompanyCd().toUpperCase(), LinkedHashMap::new, Collectors.toList()));
    }

    /** 최근에 찾은 근거부터, 같은 공고는 한 번만 */
    private static List<FeatureEvidenceDto> toEvidences(List<CompanyFeature> rows) {
        Set<Long> seenJobs = new HashSet<>();
        return rows.stream()
            .sorted(Comparator.comparing(CompanyFeature::getDetectedDts,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .filter(f -> seenJobs.add(f.getJobId()))
            .limit(MAX_EVIDENCES)
            .map(FeatureEvidenceDto::from)
            .collect(Collectors.toList());
    }

    private static int companyOrder(String companyCd) {
        CompanyEnums company = CompanyEnums.fromCompanyCd(companyCd);
        return company == null ? Integer.MAX_VALUE : company.ordinal();
    }
}
