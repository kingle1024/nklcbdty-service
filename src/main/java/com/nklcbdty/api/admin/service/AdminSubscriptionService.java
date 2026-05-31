package com.nklcbdty.api.admin.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import com.nklcbdty.api.admin.dto.AdminSubscriptionDetailDto;
import com.nklcbdty.api.admin.dto.AdminSubscriptionItemDto;
import com.nklcbdty.api.admin.dto.AdminSubscriptionPageResponse;
import com.nklcbdty.api.admin.dto.AdminSubscriptionRowDto;
import com.nklcbdty.api.admin.dto.AdminSubscriptionStatsDto;
import com.nklcbdty.api.email.service.EmailService;
import com.nklcbdty.api.user.dto.UserSettingsRequest;
import com.nklcbdty.api.user.repository.UserInterestRepository;
import com.nklcbdty.api.user.repository.UserRepository;
import com.nklcbdty.api.user.service.UserInterestService;
import com.nklcbdty.api.user.vo.UserInterestVo;
import com.nklcbdty.api.user.vo.UserVo;

@Slf4j
@Service
public class AdminSubscriptionService {

    private static final String ITEM_TYPE_COMPANY = "company";
    private static final String ITEM_TYPE_JOB = "job";
    private static final String ITEM_TYPE_CAREER_YEAR = "career_year";

    private final UserInterestRepository userInterestRepository;
    private final UserRepository userRepository;
    private final UserInterestService userInterestService;
    private final EmailService emailService;

    @Autowired
    public AdminSubscriptionService(UserInterestRepository userInterestRepository,
                                    UserRepository userRepository,
                                    UserInterestService userInterestService,
                                    EmailService emailService) {
        this.userInterestRepository = userInterestRepository;
        this.userRepository = userRepository;
        this.userInterestService = userInterestService;
        this.emailService = emailService;
    }

    public AdminSubscriptionPageResponse list(int page, int size, String keyword) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 20 : size;

        List<UserInterestVo> allInterests = userInterestRepository.findAll();
        Map<String, List<UserInterestVo>> grouped = allInterests.stream()
            .collect(Collectors.groupingBy(UserInterestVo::getUserId, LinkedHashMap::new, Collectors.toList()));

        Map<String, UserVo> userMap = loadUserMap(grouped.keySet());

        List<AdminSubscriptionRowDto> allRows = grouped.entrySet().stream()
            .map(entry -> buildRow(entry.getKey(), entry.getValue(), userMap.get(entry.getKey())))
            .sorted(Comparator.comparing(AdminSubscriptionRowDto::getLatestUpdateDts,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());

        List<AdminSubscriptionRowDto> filtered = applyKeyword(allRows, keyword);

        int totalElements = filtered.size();
        int totalPages = (int) Math.ceil(totalElements / (double) safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<AdminSubscriptionRowDto> pageRows = filtered.subList(fromIndex, toIndex);

        return AdminSubscriptionPageResponse.builder()
            .rows(pageRows)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .pageNumber(safePage)
            .pageSize(safeSize)
            .build();
    }

    public AdminSubscriptionDetailDto detail(String userId) {
        List<UserInterestVo> items = userInterestRepository.findByUserId(userId);

        UserVo user = userRepository.findByUserId(userId);
        List<AdminSubscriptionItemDto> itemDtos = items.stream()
            .map(this::toItemDto)
            .sorted(Comparator.comparing(AdminSubscriptionItemDto::getItemType)
                .thenComparing(AdminSubscriptionItemDto::getItemValue))
            .collect(Collectors.toList());

        return AdminSubscriptionDetailDto.builder()
            .userId(userId)
            .username(user == null ? null : user.getUsername())
            .email(user == null ? null : user.getEmail())
            .items(itemDtos)
            .build();
    }

    @Transactional
    public void deleteItem(Long id) {
        userInterestRepository.deleteById(id);
    }

    @Transactional
    public AdminSubscriptionDetailDto updateSubscriptions(String userId, UserSettingsRequest request) {
        userInterestService.updateUserSettings(userId, request);
        return detail(userId);
    }

    @Async
    public void sendJobEmail(String userId) {
        try {
            emailService.sendJobDailyEmails(List.of(userId));
        } catch (Exception e) {
            log.error("[admin] 메일 발송 실패: userId={}, error={}", userId, e.getMessage(), e);
        }
    }

    public AdminSubscriptionStatsDto stats() {
        List<UserInterestVo> allInterests = userInterestRepository.findAll();

        Set<String> uniqueUsers = allInterests.stream()
            .map(UserInterestVo::getUserId)
            .collect(Collectors.toSet());

        Map<String, Map<String, Long>> countsByItemType = allInterests.stream()
            .collect(Collectors.groupingBy(
                UserInterestVo::getItemType,
                LinkedHashMap::new,
                Collectors.groupingBy(UserInterestVo::getItemValue, Collectors.counting())
            ));

        return AdminSubscriptionStatsDto.builder()
            .totalSubscribers(uniqueUsers.size())
            .totalItems(allInterests.size())
            .countsByItemType(countsByItemType)
            .build();
    }

    private Map<String, UserVo> loadUserMap(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserVo> users = userRepository.findByUserIdIn(new ArrayList<>(userIds));
        Map<String, UserVo> result = new HashMap<>();
        for (UserVo user : users) {
            result.put(user.getUserId(), user);
        }
        return result;
    }

    private AdminSubscriptionRowDto buildRow(String userId, List<UserInterestVo> items, UserVo user) {
        List<String> companies = filterValues(items, ITEM_TYPE_COMPANY);
        List<String> jobs = filterValues(items, ITEM_TYPE_JOB);
        Integer careerYear = extractLatestCareerYear(items);
        LocalDateTime latestUpdate = items.stream()
            .map(UserInterestVo::getUpdateDts)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .orElse(null);

        return AdminSubscriptionRowDto.builder()
            .userId(userId)
            .username(user == null ? null : user.getUsername())
            .email(user == null ? null : user.getEmail())
            .companies(companies)
            .jobs(jobs)
            .careerYear(careerYear)
            .latestUpdateDts(latestUpdate)
            .build();
    }

    private List<String> filterValues(List<UserInterestVo> items, String itemType) {
        return items.stream()
            .filter(o -> itemType.equals(o.getItemType()))
            .map(UserInterestVo::getItemValue)
            .collect(Collectors.toList());
    }

    private Integer extractLatestCareerYear(List<UserInterestVo> items) {
        Optional<UserInterestVo> latest = items.stream()
            .filter(o -> ITEM_TYPE_CAREER_YEAR.equals(o.getItemType()))
            .max(Comparator.comparing(UserInterestVo::getUpdateDts,
                Comparator.nullsLast(Comparator.naturalOrder())));
        if (latest.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(latest.get().getItemValue());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<AdminSubscriptionRowDto> applyKeyword(List<AdminSubscriptionRowDto> rows, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return rows;
        }
        String needle = keyword.toLowerCase();
        return rows.stream()
            .filter(row -> matches(row, needle))
            .collect(Collectors.toList());
    }

    private boolean matches(AdminSubscriptionRowDto row, String needle) {
        if (containsIgnoreCase(row.getUserId(), needle)) return true;
        if (containsIgnoreCase(row.getUsername(), needle)) return true;
        if (containsIgnoreCase(row.getEmail(), needle)) return true;
        if (anyContains(row.getCompanies(), needle)) return true;
        if (anyContains(row.getJobs(), needle)) return true;
        return false;
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private boolean anyContains(List<String> values, String needle) {
        if (values == null) return false;
        for (String v : values) {
            if (containsIgnoreCase(v, needle)) return true;
        }
        return false;
    }

    private AdminSubscriptionItemDto toItemDto(UserInterestVo vo) {
        return AdminSubscriptionItemDto.builder()
            .id(vo.getId())
            .itemType(vo.getItemType())
            .itemValue(vo.getItemValue())
            .insertDts(vo.getInsertDts())
            .updateDts(vo.getUpdateDts())
            .build();
    }
}
