package com.nklcbdty.api.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nklcbdty.api.user.dto.UserSettingsRequest;
import com.nklcbdty.api.user.repository.UserInterestQueryRepository;
import com.nklcbdty.common.user.repository.UserInterestRepository;
import com.nklcbdty.common.vo.UserInterestVo;

@ExtendWith(MockitoExtension.class)
class UserInterestServiceTest {

    private static final String USER_ID = "kakao@2614615415";

    @Mock
    private UserInterestRepository repository;

    @Mock
    private UserInterestQueryRepository queryRepository;

    @InjectMocks
    private UserInterestService service;

    // ------------------------------------------------------------ 경력 조회

    @Test
    void findCareerYear_설정한적이_없으면_null() {
        when(queryRepository.findByUserIdAndItemTypeOrderByIdAsc(USER_ID, "career_year")).thenReturn(List.of());

        assertThat(service.findCareerYear(USER_ID)).isNull();
    }

    @Test
    void findCareerYear_과거_중복행이_남아있으면_가장_나중에_들어온_값을_쓴다() {
        when(queryRepository.findByUserIdAndItemTypeOrderByIdAsc(USER_ID, "career_year")).thenReturn(List.of(
            interest(22L, "2"),
            interest(23L, "3"),
            interest(25L, "5")
        ));

        assertThat(service.findCareerYear(USER_ID)).isEqualTo(5);
    }

    @Test
    void findCareerYear_숫자가_아닌_값이_섞여있어도_깨지지_않는다() {
        when(queryRepository.findByUserIdAndItemTypeOrderByIdAsc(USER_ID, "career_year")).thenReturn(List.of(
            interest(1L, "3"),
            interest(2L, "몰라요")
        ));

        assertThat(service.findCareerYear(USER_ID)).isEqualTo(3);
    }

    // ------------------------------------------------------------ 경력 저장

    @Test
    void updateUserSettings_경력은_기존행을_지우고_1건만_남긴다() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of());

        service.updateUserSettings(USER_ID, request("3"));

        verify(queryRepository).deleteByUserIdAndItemType(USER_ID, "career_year");

        ArgumentCaptor<List<UserInterestVo>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getItemType()).isEqualTo("career_year");
        assertThat(captor.getValue().get(0).getItemValue()).isEqualTo("3");
    }

    @Test
    void updateUserSettings_경력값이_없으면_기존_설정을_지우지_않는다() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of());

        service.updateUserSettings(USER_ID, request(null));

        verify(queryRepository, never()).deleteByUserIdAndItemType(anyString(), anyString());
        verify(repository, never()).saveAll(anyList());
    }

    // ------------------------------------------------------------------ 헬퍼

    private UserInterestVo interest(Long id, String value) {
        return UserInterestVo.builder()
            .id(id)
            .userId(USER_ID)
            .itemType("career_year")
            .itemValue(value)
            .build();
    }

    private UserSettingsRequest request(String careerYear) {
        UserSettingsRequest request = new UserSettingsRequest();
        request.setSelectedCareerYears(careerYear);
        return request;
    }
}
