package com.nklcbdty.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import com.nklcbdty.api.admin.dto.AdminSubscriptionDetailDto;
import com.nklcbdty.api.admin.dto.AdminSubscriptionPageResponse;
import com.nklcbdty.api.admin.dto.AdminSubscriptionRowDto;
import com.nklcbdty.api.admin.dto.AdminSubscriptionStatsDto;
import com.nklcbdty.api.email.service.EmailService;
import com.nklcbdty.api.user.dto.UserSettingsRequest;
import com.nklcbdty.api.user.repository.UserInterestRepository;
import com.nklcbdty.api.user.repository.UserRepository;
import com.nklcbdty.api.user.service.UserInterestService;
import com.nklcbdty.common.vo.UserInterestVo;
import com.nklcbdty.api.user.vo.UserVo;

class AdminSubscriptionServiceTest {

    private UserInterestRepository userInterestRepository;
    private UserRepository userRepository;
    private UserInterestService userInterestService;
    private EmailService emailService;
    private AdminSubscriptionService service;

    @BeforeEach
    void setUp() {
        userInterestRepository = mock(UserInterestRepository.class);
        userRepository = mock(UserRepository.class);
        userInterestService = mock(UserInterestService.class);
        emailService = mock(EmailService.class);
        service = new AdminSubscriptionService(
            userInterestRepository, userRepository, userInterestService, emailService
        );
    }

    @Test
    @DisplayName("list: user_id 별로 그룹핑하여 회사/직무/경력년수를 묶어 반환한다")
    void list_groupsByUserId() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 10, 0);
        when(userInterestRepository.findAll()).thenReturn(List.of(
            interest(1L, "kakao@1", "company", "naver", now),
            interest(2L, "kakao@1", "company", "kakao", now),
            interest(3L, "kakao@1", "job", "Backend", now),
            interest(4L, "kakao@1", "career_year", "3", now),
            interest(5L, "kakao@2", "company", "toss", now.minusDays(1)),
            interest(6L, "kakao@2", "job", "Frontend", now.minusDays(1))
        ));
        when(userRepository.findByUserIdIn(any())).thenReturn(List.of(
            user("kakao@1", "u1", "u1@test.com"),
            user("kakao@2", "u2", "u2@test.com")
        ));

        AdminSubscriptionPageResponse response = service.list(0, 20, null);

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.getRows()).extracting(AdminSubscriptionRowDto::getUserId)
            .containsExactly("kakao@1", "kakao@2");

        AdminSubscriptionRowDto first = response.getRows().get(0);
        assertThat(first.getCompanies()).containsExactlyInAnyOrder("naver", "kakao");
        assertThat(first.getJobs()).containsExactly("Backend");
        assertThat(first.getCareerYear()).isEqualTo(3);
        assertThat(first.getEmail()).isEqualTo("u1@test.com");
    }

    @Test
    @DisplayName("list: keyword가 주어지면 userId/email/회사/직무를 부분 일치로 필터링한다")
    void list_filtersByKeyword() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 10, 0);
        when(userInterestRepository.findAll()).thenReturn(List.of(
            interest(1L, "kakao@1", "company", "naver", now),
            interest(2L, "kakao@2", "company", "toss", now)
        ));
        when(userRepository.findByUserIdIn(any())).thenReturn(List.of(
            user("kakao@1", "u1", "u1@test.com"),
            user("kakao@2", "u2", "u2@test.com")
        ));

        AdminSubscriptionPageResponse response = service.list(0, 20, "toss");

        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getRows().get(0).getUserId()).isEqualTo("kakao@2");
    }

    @Test
    @DisplayName("list: 페이징 파라미터(page/size)로 분할된다")
    void list_appliesPagination() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 1, 10, 0);
        List<UserInterestVo> all = List.of(
            interest(1L, "u1", "company", "naver", base),
            interest(2L, "u2", "company", "kakao", base.minusMinutes(1)),
            interest(3L, "u3", "company", "toss", base.minusMinutes(2)),
            interest(4L, "u4", "company", "line", base.minusMinutes(3)),
            interest(5L, "u5", "company", "coupang", base.minusMinutes(4))
        );
        when(userInterestRepository.findAll()).thenReturn(all);
        when(userRepository.findByUserIdIn(any())).thenReturn(List.of());

        AdminSubscriptionPageResponse page0 = service.list(0, 2, null);
        AdminSubscriptionPageResponse page1 = service.list(1, 2, null);
        AdminSubscriptionPageResponse page2 = service.list(2, 2, null);

        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.getRows()).extracting(AdminSubscriptionRowDto::getUserId)
            .containsExactly("u1", "u2");
        assertThat(page1.getRows()).extracting(AdminSubscriptionRowDto::getUserId)
            .containsExactly("u3", "u4");
        assertThat(page2.getRows()).extracting(AdminSubscriptionRowDto::getUserId)
            .containsExactly("u5");
    }

    @Test
    @DisplayName("detail: 특정 유저의 모든 user_interest 항목과 회원 정보를 함께 반환한다")
    void detail_returnsItemsAndUser() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 10, 0);
        when(userInterestRepository.findByUserId("kakao@1")).thenReturn(List.of(
            interest(1L, "kakao@1", "company", "naver", now),
            interest(2L, "kakao@1", "job", "Backend", now)
        ));
        when(userRepository.findByUserId("kakao@1"))
            .thenReturn(user("kakao@1", "name", "n@test.com"));

        AdminSubscriptionDetailDto detail = service.detail("kakao@1");

        assertThat(detail.getUserId()).isEqualTo("kakao@1");
        assertThat(detail.getEmail()).isEqualTo("n@test.com");
        assertThat(detail.getItems()).hasSize(2);
        assertThat(detail.getItems()).extracting("itemType")
            .containsExactly("company", "job");
    }

    @Test
    @DisplayName("detail: 회원 정보가 없어도 user_interest 항목은 반환된다")
    void detail_whenUserMissing_returnsItemsOnly() {
        when(userInterestRepository.findByUserId("orphan")).thenReturn(List.of(
            interest(99L, "orphan", "company", "naver", LocalDateTime.now())
        ));
        when(userRepository.findByUserId("orphan")).thenReturn(null);

        AdminSubscriptionDetailDto detail = service.detail("orphan");

        assertThat(detail.getUserId()).isEqualTo("orphan");
        assertThat(detail.getEmail()).isNull();
        assertThat(detail.getUsername()).isNull();
        assertThat(detail.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("deleteItem: repository.deleteById를 호출한다")
    void deleteItem_callsRepository() {
        service.deleteItem(42L);
        verify(userInterestRepository).deleteById(eq(42L));
    }

    @Test
    @DisplayName("updateSubscriptions: UserInterestService.updateUserSettings를 호출하고 변경된 detail을 반환한다")
    void updateSubscriptions_delegatesToUserInterestService() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 0);
        UserSettingsRequest request = new UserSettingsRequest();
        request.setSubscribedServices(List.of("naver", "toss"));
        request.setSelectedJobRoles(List.of("Backend"));
        request.setSelectedCareerYears("5");

        when(userInterestRepository.findByUserId("kakao@1")).thenReturn(List.of(
            interest(11L, "kakao@1", "company", "naver", now),
            interest(12L, "kakao@1", "company", "toss", now),
            interest(13L, "kakao@1", "job", "Backend", now),
            interest(14L, "kakao@1", "career_year", "5", now)
        ));
        when(userRepository.findByUserId("kakao@1"))
            .thenReturn(user("kakao@1", "name", "n@test.com"));

        AdminSubscriptionDetailDto result = service.updateSubscriptions("kakao@1", request);

        verify(userInterestService).updateUserSettings(eq("kakao@1"), eq(request));
        assertThat(result.getUserId()).isEqualTo("kakao@1");
        assertThat(result.getItems()).extracting("itemValue")
            .contains("naver", "toss", "Backend", "5");
    }

    @Test
    @DisplayName("sendJobEmail: 발송 대상 이메일이 있으면 EmailService.sendEmail로 본문을 발송한다")
    void sendJobEmail_dispatchesMail() {
        when(emailService.sendEmail(eq(List.of("kakao@1"))))
            .thenReturn(Map.of("user@test.com", "<html>본문</html>"));

        service.sendJobEmail("kakao@1");

        verify(emailService).sendEmail(eq("user@test.com"), any(String.class), eq("<html>본문</html>"));
    }

    @Test
    @DisplayName("sendJobEmail: 발송할 컨텐츠가 없으면 EmailService.sendEmail(to,...)을 호출하지 않는다")
    void sendJobEmail_skipsWhenEmpty() {
        when(emailService.sendEmail(eq(List.of("kakao@1")))).thenReturn(Map.of());

        service.sendJobEmail("kakao@1");

        verify(emailService, org.mockito.Mockito.never())
            .sendEmail(any(String.class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("stats: 총 구독자 수와 itemType별 itemValue 카운트를 집계한다")
    void stats_aggregatesCounts() {
        LocalDateTime now = LocalDateTime.now();
        when(userInterestRepository.findAll()).thenReturn(List.of(
            interest(1L, "u1", "company", "naver", now),
            interest(2L, "u1", "company", "kakao", now),
            interest(3L, "u2", "company", "naver", now),
            interest(4L, "u2", "job", "Backend", now),
            interest(5L, "u3", "job", "Backend", now),
            interest(6L, "u3", "job", "Frontend", now)
        ));

        AdminSubscriptionStatsDto stats = service.stats();

        assertThat(stats.getTotalSubscribers()).isEqualTo(3);
        assertThat(stats.getTotalItems()).isEqualTo(6);
        assertThat(stats.getCountsByItemType().get("company"))
            .containsEntry("naver", 2L)
            .containsEntry("kakao", 1L);
        assertThat(stats.getCountsByItemType().get("job"))
            .containsEntry("Backend", 2L)
            .containsEntry("Frontend", 1L);
    }

    private UserInterestVo interest(Long id, String userId, String type, String value, LocalDateTime updateDts) {
        UserInterestVo vo = new UserInterestVo();
        vo.setId(id);
        vo.setUserId(userId);
        vo.setItemType(type);
        vo.setItemValue(value);
        vo.setInsertDts(updateDts);
        vo.setUpdateDts(updateDts);
        return vo;
    }

    private UserVo user(String userId, String username, String email) {
        return UserVo.builder()
            .userId(userId)
            .username(username)
            .email(email)
            .build();
    }
}
