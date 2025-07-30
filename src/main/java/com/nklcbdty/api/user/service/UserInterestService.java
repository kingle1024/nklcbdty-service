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
import com.nklcbdty.api.user.repository.UserInterestRepository;
import com.nklcbdty.api.user.vo.UserInterestVo;

@Service
public class UserInterestService {
    private final UserInterestRepository repository;

    @Autowired
    public UserInterestService(UserInterestRepository repository) {
        this.repository = repository;
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
        List<String> career_year = new ArrayList<>();
        career_year.add(userSettings.getSelectedCareerYears());
        processInsertions(userId, "career_year", career_year);

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
