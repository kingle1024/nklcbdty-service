package com.nklcbdty.api.user.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.user.dto.UserInterestResponseDto;
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
}
