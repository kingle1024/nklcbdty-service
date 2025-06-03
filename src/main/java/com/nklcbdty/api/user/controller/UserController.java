package com.nklcbdty.api.user.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.user.dto.UserInterestResponseDto;
import com.nklcbdty.api.user.dto.UserSettingsRequest;
import com.nklcbdty.api.user.service.UserInterestService;
import com.nklcbdty.api.user.service.UserService;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    private final UserInterestService userInterestService;

    @Autowired
    public UserController(UserService userService, UserInterestService userInterestService) {
        this.userService = userService;
        this.userInterestService = userInterestService;
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getUserSettings(HttpServletRequest request) {
        final String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                .body(Map.of("error", "로그인 정보가 없습니다."));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("userInfo", userService.findByUserId(userId));
        List<UserInterestResponseDto> interestItems = userInterestService.findByUserId(userId);
        List<String> companys = interestItems.stream()
            .filter(o -> "company".equals(o.getItemType()))
            .map(UserInterestResponseDto::getItemValue)
            .collect(Collectors.toList());
        List<String> jobs = interestItems.stream()
            .filter(o -> "job".equals(o.getItemType()))
            .map(UserInterestResponseDto::getItemValue)
            .collect(Collectors.toList());

        result.put("subscribedServices", companys);
        result.put("selectedJobRoles", jobs);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/settings")
    public ResponseEntity<?> setUserSettings(
        HttpServletRequest request,
        @RequestBody UserSettingsRequest userSettings
    ) {
        final String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                .body(Map.of("error", "로그인 정보가 없습니다."));
        }

        try {
            userInterestService.updateUserSettings(userId, userSettings);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "사용자 설정 업데이트 중 오류가 발생했습니다.", "message", e.getMessage()));
        }
    }
}
