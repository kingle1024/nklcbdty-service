package com.nklcbdty.api.user.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.user.dto.DeltaResult;
import com.nklcbdty.api.user.dto.UserInterestResponseDto;
import com.nklcbdty.api.user.dto.UserSettingsRequest;
import com.nklcbdty.api.user.repository.UserInterestQueryRepository;
import com.nklcbdty.common.user.repository.UserInterestRepository;
import com.nklcbdty.common.vo.UserInterestVo;

@Service
public class UserInterestService {
    /** 경력은 한 사람당 하나뿐인 값이라 다른 itemType 과 달리 항상 1건만 유지한다. */
    private static final String ITEM_TYPE_CAREER_YEAR = "career_year";

    private final UserInterestRepository repository;
    private final UserInterestQueryRepository queryRepository;

    @Autowired
    public UserInterestService(UserInterestRepository repository, UserInterestQueryRepository queryRepository) {
        this.repository = repository;
        this.queryRepository = queryRepository;
    }

    public List<UserInterestResponseDto> findByUserId(String userId) {
        List<UserInterestVo> items = repository.findByUserId(userId);
        return items.stream()
            .map(item -> UserInterestResponseDto.builder()
                .itemType(item.getItemType())
                .itemValue(item.getItemValue())
                .build()
            )
            .collect(Collectors.toList());
    }

    @Transactional
    public void updateUserSettings(String userId, UserSettingsRequest userSettings) {
        List<UserInterestResponseDto> interestItems = findByUserId(userId);
        List<String> originCompanys = extractItemValues(interestItems, "company");
        List<String> originJobs = extractItemValues(interestItems, "job");

        // 2. 새로 선택된 목록 가져오기 및 초기화
        List<String> selectCompanys = userSettings.getSubscribedServices() == null ? new ArrayList<>() : userSettings.getSubscribedServices();
        List<String> selectJobs = userSettings.getSelectedJobRoles() == null ? new ArrayList<>() : userSettings.getSelectedJobRoles();

        // 3. 삭제 대상 및 삽입 대상 계산 (Delta 계산)
        DeltaResult companyDelta = calculateDelta(originCompanys, selectCompanys);
        DeltaResult jobDelta = calculateDelta(originJobs, selectJobs);

        // 4. 데이터베이스 삭제 처리 (processDeletions 사용)
        processDeletions(userId, "company", companyDelta.getToDelete());
        processDeletions(userId, "job", jobDelta.getToDelete());

        // 5. 데이터베이스 삽입 처리 (processInsertions 사용)
        processInsertions(userId, "company", companyDelta.getToInsert());
        processInsertions(userId, "job", jobDelta.getToInsert());
        applyCareerYear(userId, userSettings.getSelectedCareerYears());

        // 6. 변경 없이 유지된 항목 식별
        Set<String> companysRetained = originCompanys.stream()
           .filter(origin -> !companyDelta.getToDelete().contains(origin))
           .collect(Collectors.toSet());
        Set<String> jobsRetained = originJobs.stream()
           .filter(origin -> !jobDelta.getToDelete().contains(origin))
           .collect(Collectors.toSet());

        // 7. 유지된 항목의 update_dts 업데이트 (processRetainedUpdates 사용)
        processRetainedUpdates(userId, companysRetained, jobsRetained);
    }

    /**
     * 저장된 경력 연차를 돌려줍니다. 설정한 적이 없으면 null.
     *
     * <p>경력은 {@link #applyCareerYear} 로 항상 1건만 유지하지만, 저장할 때마다 INSERT 만 해서
     * 쌓인 예전 행이 남아 있을 수 있어 가장 나중에 들어온 행을 쓴다. id 순서를 명시해 조회하므로
     * (정렬 없는 findByUserId 와 달리) 어떤 값이 나올지 보장된다.
     */
    public Integer findCareerYear(String userId) {
        List<UserInterestVo> rows =
            queryRepository.findByUserIdAndItemTypeOrderByIdAsc(userId, ITEM_TYPE_CAREER_YEAR);

        for (int i = rows.size() - 1; i >= 0; i--) {
            Integer careerYear = parseCareerYear(rows.get(i).getItemValue());
            if (careerYear != null) {
                return careerYear;
            }
        }
        return null;
    }

    /**
     * 경력 연차를 1건만 남도록 저장합니다. 값이 넘어오지 않으면 기존 설정을 그대로 둡니다.
     */
    private void applyCareerYear(String userId, String selectedCareerYears) {
        if (parseCareerYear(selectedCareerYears) == null) {
            return;
        }

        queryRepository.deleteByUserIdAndItemType(userId, ITEM_TYPE_CAREER_YEAR);
        processInsertions(userId, ITEM_TYPE_CAREER_YEAR, List.of(selectedCareerYears.trim()));
    }

    /** 숫자가 아닌 값(과거 데이터)이 섞여 있어도 조회가 깨지지 않도록 null 로 넘긴다. */
    private Integer parseCareerYear(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * UserInterestVo 목록에서 특정 itemType에 해당하는 itemValue 목록을 추출합니다.
     */
    private List<String> extractItemValues(List<UserInterestResponseDto> interests, String itemType) {
        return interests.stream()
            .filter(o -> itemType.equals(o.getItemType()))
            .map(UserInterestResponseDto::getItemValue)
            .collect(Collectors.toList());
    }

    /**
     * 원본 목록과 새 목록을 비교하여 삭제 대상과 삽입 대상을 계산합니다.
     */
    private DeltaResult calculateDelta(List<String> originList, List<String> selectList) {
        List<String> toDelete = new ArrayList<>(originList);
        toDelete.removeAll(selectList);

        List<String> toInsert = new ArrayList<>(selectList);
        toInsert.removeAll(originList);

        return DeltaResult.builder()
            .toDelete(toDelete)
            .toInsert(toInsert)
            .build();
    }

    /**
     * 삭제 대상 항목들을 데이터베이스에서 삭제합니다.
     */
    private void processDeletions(String userId, String itemType, List<String> valuesToDelete) {
        if (!valuesToDelete.isEmpty()) {
            repository.deleteByUserIdAndItemTypeAndItemValueIn(userId, itemType, valuesToDelete);
        }
    }

    /**
     * 삽입 대상 항목들을 새로운 엔티티로 만들어 데이터베이스에 저장합니다.
     */
    private void processInsertions(String userId, String itemType, List<String> valuesToInsert) {
        if (!valuesToInsert.isEmpty()) {
            List<UserInterestVo> entitiesToInsert = new ArrayList<>();
            for (String value : valuesToInsert) {
                 UserInterestVo newInterest = UserInterestVo.builder() // Builder 사용 가정
                    .userId(userId)
                    .itemType(itemType)
                    .itemValue(value)
                    .build();
                 entitiesToInsert.add(newInterest);
            }
            repository.saveAll(entitiesToInsert);
        }
    }

    /**
     * 변경 없이 유지된 항목들의 update_dts를 업데이트합니다.
     */
    private void processRetainedUpdates(String userId, Set<String> companysRetained, Set<String> jobsRetained) {
        Set<String> retainedItemValues = new HashSet<>(companysRetained);
        retainedItemValues.addAll(jobsRetained);

        if (!retainedItemValues.isEmpty()) {
            List<UserInterestVo> retainedEntities = repository.findByUserIdAndItemValueIn(userId, new ArrayList<>(retainedItemValues)); // Set을 List로 변환

            // 조회된 각 엔티티의 update_dts를 현재 시간으로 설정하여 변경을 유도합니다.
            LocalDateTime now = LocalDateTime.now();
            for (UserInterestVo entity : retainedEntities) {
                 // 여기서 한 번 더 itemType/itemValue 확인 로직을 넣을 수도 있지만, findByUserIdAndItemValueIn 쿼리가 정확하다면 생략 가능
                 entity.setUpdateDts(now); // @UpdateTimestamp가 있어도 명시적 설정이 확실 (Setter 필요)
            }
            // 트랜잭션 종료 시점에 변경된 엔티티들이 자동으로 업데이트됩니다. save 호출은 필수는 아니지만 명시적으로 호출할 수도 있습니다.
            // repository.saveAll(retainedEntities); // 필요에 따라 saveAll 호출 고려
        }
    }
}
