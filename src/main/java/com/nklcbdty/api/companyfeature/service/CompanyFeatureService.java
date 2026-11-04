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
import com.nklcbdty.api.companyfeature.vo.WelfareMatch;
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
     * 근무형태 + 복지(모두 가진 회사만, AND). 기존 {@code /work-style/{workStyle}} 경로용.
     */
    public List<WorkStyleCompanyDto> findCompanies(WorkStyle workStyle, Collection<String> welfareCodes) {
        return findCompanies(workStyle, welfareCodes, WelfareMatch.ALL);
    }

    /**
     * @param workStyle    null 이면 근무형태로 거르지 않고, 복지 근거가 있는 회사 전체에서 고른다.
     * @param welfareCodes 비활성/없는 코드는 무시한다 (지난 링크로 들어와도 목록이 통째로 비지 않게).
     * @param match        복지를 여러 개 골랐을 때 모두(ALL) / 하나라도(ANY).
     */
    public List<WorkStyleCompanyDto> findCompanies(WorkStyle workStyle, Collection<String> welfareCodes,
                                                   WelfareMatch match) {
        Map<String, List<CompanyFeature>> styleByCompany = workStyle == null ? null : groupByCompany(
            featureRepository.findByFeatureTypeAndFeatureCode(FeatureType.WORK_STYLE, workStyle.name()));
        if (styleByCompany != null && styleByCompany.isEmpty()) {
            return List.of();
        }

        List<WelfareCategory> activeCategories = categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        Set<String> activeCodes = activeCategories.stream()
            .map(WelfareCategory::getCode)
            .collect(Collectors.toSet());

        Set<String> required = welfareCodes == null ? Set.of() : welfareCodes.stream()
            .filter(code -> code != null)
            .map(code -> code.trim().toUpperCase())
            .filter(activeCodes::contains)
            .collect(Collectors.toSet());

        Map<String, List<CompanyFeature>> welfareByCompany = groupByCompany(styleByCompany == null
            ? featureRepository.findByFeatureType(FeatureType.WELFARE)
            : featureRepository.findByFeatureTypeAndCompanyCdIn(FeatureType.WELFARE, styleByCompany.keySet()));

        // 근무형태를 고르지 않았으면 복지 근거가 있는 회사가 후보
        Set<String> candidates = styleByCompany == null ? welfareByCompany.keySet() : styleByCompany.keySet();

        List<WorkStyleCompanyDto> result = new ArrayList<>();
        for (String companyCd : candidates) {
            Map<String, List<CompanyFeature>> welfareByCode = welfareByCompany
                .getOrDefault(companyCd, List.of()).stream()
                .filter(f -> activeCodes.contains(f.getFeatureCode()))
                .collect(Collectors.groupingBy(CompanyFeature::getFeatureCode));

            if (!matchesWelfare(welfareByCode.keySet(), required, match)) {
                continue;
            }
            if (styleByCompany == null && welfareByCode.isEmpty()) {
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
            // 근무형태로 골랐으면 그 근거 공고 수, 아니면 (고른 복지가 있으면 그 복지의) 근거 공고 수
            List<CompanyFeature> countedRows = styleByCompany != null
                ? styleByCompany.get(companyCd)
                : welfareByCode.entrySet().stream()
                    .filter(e -> required.isEmpty() || required.contains(e.getKey()))
                    .flatMap(e -> e.getValue().stream())
                    .collect(Collectors.toList());
            result.add(WorkStyleCompanyDto.builder()
                .companyCd(companyCd)
                .companyNm(company == null ? companyCd : company.getCompanyNm())
                .careerPageUrl(company == null ? null : company.getCareerPageUrl())
                .workStyle(workStyle == null ? null : workStyle.name())
                .workStyleLabel(workStyle == null ? null : workStyle.getLabel())
                .postingCount((int) countedRows.stream().map(CompanyFeature::getJobId).distinct().count())
                .evidences(styleByCompany == null ? List.of() : toEvidences(countedRows))
                .welfares(welfares)
                .build());
        }

        // 근거 공고가 많은 회사가 위로. 같으면 회사 enum 순서(목록 화면 탭 순서)
        result.sort(Comparator.comparingInt(WorkStyleCompanyDto::getPostingCount).reversed()
            .thenComparingInt(dto -> companyOrder(dto.getCompanyCd())));
        return result;
    }

    /** 고른 복지가 없으면 통과. ALL 은 모두, ANY 는 하나라도 */
    private static boolean matchesWelfare(Set<String> has, Set<String> required, WelfareMatch match) {
        if (required.isEmpty()) {
            return true;
        }
        return match == WelfareMatch.ANY
            ? required.stream().anyMatch(has::contains)
            : has.containsAll(required);
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
