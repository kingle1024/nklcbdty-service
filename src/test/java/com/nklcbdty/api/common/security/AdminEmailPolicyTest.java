package com.nklcbdty.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.nklcbdty.common.user.repository.UserRepository;
import com.nklcbdty.common.vo.UserVo;

class AdminEmailPolicyTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    @Test
    void 등록된_이메일이면_대소문자_공백을_무시하고_관리자로_본다() {
        AdminEmailPolicy policy = new AdminEmailPolicy("teran1024@naver.com", userRepository);

        assertThat(policy.isAdminEmail("teran1024@naver.com")).isTrue();
        assertThat(policy.isAdminEmail(" Teran1024@Naver.com ")).isTrue();
        assertThat(policy.isAdminEmail("other@naver.com")).isFalse();
        assertThat(policy.isAdminEmail(null)).isFalse();
    }

    @Test
    void 목록은_쉼표로_여러_개_지정할_수_있다() {
        AdminEmailPolicy policy = new AdminEmailPolicy("a@x.com, b@x.com ,", userRepository);

        assertThat(policy.isAdminEmail("a@x.com")).isTrue();
        assertThat(policy.isAdminEmail("b@x.com")).isTrue();
        assertThat(policy.isAdminEmail("")).isFalse();
    }

    @Test
    void userId_로_찾을_땐_user_테이블_이메일을_보고_표시이름을_돌려준다() {
        when(userRepository.findByUserId("local@3")).thenReturn(
            UserVo.builder().userId("local@3").username("관리자").email("teran1024@naver.com").build());
        AdminEmailPolicy policy = new AdminEmailPolicy("teran1024@naver.com", userRepository);

        assertThat(policy.adminNameByUserId("local@3")).isEqualTo("관리자");
    }

    @Test
    void 프로필이_없거나_다른_이메일이면_관리자가_아니다() {
        when(userRepository.findByUserId("local@9")).thenReturn(null);
        when(userRepository.findByUserId("local@4")).thenReturn(
            UserVo.builder().userId("local@4").username("유저").email("user@example.com").build());
        AdminEmailPolicy policy = new AdminEmailPolicy("teran1024@naver.com", userRepository);

        assertThat(policy.adminNameByUserId("local@9")).isNull();
        assertThat(policy.adminNameByUserId("local@4")).isNull();
        assertThat(policy.adminNameByUserId(null)).isNull();
    }

    @Test
    void 조회가_터져도_일반_사용자로_넘어간다() {
        when(userRepository.findByUserId("local@3")).thenThrow(new RuntimeException("DB down"));
        AdminEmailPolicy policy = new AdminEmailPolicy("teran1024@naver.com", userRepository);

        assertThat(policy.adminNameByUserId("local@3")).isNull();
    }
}
