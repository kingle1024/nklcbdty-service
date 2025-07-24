package com.nklcbdty.api.user.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.user.dto.UserResponseDto;
import com.nklcbdty.api.user.repository.UserIdAndEmailDto;
import com.nklcbdty.api.user.repository.UserRepository;
import com.nklcbdty.api.auth.service.UserDetailService;
import com.nklcbdty.api.user.vo.UserVo;

@Service
public class UserService implements UserDetailService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserById(String userId, String username, String refreshToken) throws
        UsernameNotFoundException {
        UserVo user = userRepository.findByUserId(userId);
        UserDetails userDetails;
        String paramUserName;

        if(user == null) {
            userRepository.save(UserVo.builder()
                .userId(userId)
                .username(username)
                .build());
            paramUserName = username;
        } else {
            paramUserName = user.getUsername();
        }

        userDetails = new org.springframework.security.core.userdetails.User(
            paramUserName,
            "user.getPassword()",
            new ArrayList<>()
        );

        return userDetails;
    }

    public UserResponseDto findByUserId(String userId) {
        UserVo user = userRepository.findByUserId(userId);
        return UserResponseDto.builder()
            .username(user.getUsername())
            .email(user.getEmail())
            .build();
    }

    public List<UserIdAndEmailDto> findByUserIdIn(List<String> userIds) {
        List<UserVo> items = userRepository.findByUserIdIn(userIds);
        List<UserIdAndEmailDto> results = new ArrayList<>();
        for (UserVo item : items) {
            results.add(new UserIdAndEmailDto(item.getUserId(), item.getEmail()));
        }

        return results;
    }
}
