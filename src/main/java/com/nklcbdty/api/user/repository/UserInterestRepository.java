package com.nklcbdty.api.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.user.vo.UserInterestVo;

public interface UserInterestRepository extends JpaRepository<UserInterestVo, Long>, UserInterestRepositoryCustom {
    List<UserInterestVo> findByUserId(String userId);
    void deleteByUserIdAndItemTypeAndItemValueIn(String userId, String itemType, List<String> itemValues);
    List<UserInterestVo> findByUserIdAndItemValueIn(String userId, List<String> itemValues);
    List<UserInterestVo> findItemValueByUserIdAndItemType(String userId, String company);
}
