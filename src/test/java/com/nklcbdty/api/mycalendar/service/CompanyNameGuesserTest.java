package com.nklcbdty.api.mycalendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * URL → 회사명 추측. 틀린 이름을 자신 있게 채워 넣는 것보다 비워 두는 게 낫다는 것이
 * 이 클래스의 원칙이라, "포기해야 하는 경우" 도 함께 고정한다.
 */
class CompanyNameGuesserTest {

    @Test
    @DisplayName("우리가 아는 회사의 채용 페이지는 한글 회사명으로 준다")
    void knownCompaniesBecomeKoreanNames() {
        assertThat(CompanyNameGuesser.guess("https://careers.kakao.com/jobs/P-12345")).isEqualTo("카카오");
        assertThat(CompanyNameGuesser.guess("https://recruit.navercorp.com/rcrt/list.do")).isEqualTo("네이버");
        assertThat(CompanyNameGuesser.guess("https://career.woowahan.com/recruitment/R2312")).isEqualTo("배달의민족");
        assertThat(CompanyNameGuesser.guess("https://careers.daangn.com/jobs/1234")).isEqualTo("당근마켓");
        assertThat(CompanyNameGuesser.guess("https://toss.im/career/job-detail?job_id=1")).isEqualTo("토스");
        assertThat(CompanyNameGuesser.guess("https://www.coupang.jobs/kr/jobs/12345/")).isEqualTo("쿠팡");
        assertThat(CompanyNameGuesser.guess("https://careers.linecorp.com/ko/jobs/1234")).isEqualTo("라인");
    }

    @Test
    @DisplayName("채용 솔루션(ATS) 주소는 정해진 자리에서 회사 슬러그를 꺼낸다")
    void atsUrlsYieldTheTenantSlug() {
        // 경로 첫 조각이 회사
        assertThat(CompanyNameGuesser.guess("https://boards.greenhouse.io/stripe/jobs/4567")).isEqualTo("Stripe");
        assertThat(CompanyNameGuesser.guess("https://job-boards.greenhouse.io/coupang/jobs/1")).isEqualTo("쿠팡");
        assertThat(CompanyNameGuesser.guess("https://jobs.lever.co/figma/abc-def")).isEqualTo("Figma");
        assertThat(CompanyNameGuesser.guess("https://jobs.ashbyhq.com/linear/xyz")).isEqualTo("Linear");
        assertThat(CompanyNameGuesser.guess("https://apply.workable.com/some-startup/j/ABC/")).isEqualTo("Some Startup");
        // 맨 앞 라벨이 회사 (야놀자 채용은 Workday 를 쓴다)
        assertThat(CompanyNameGuesser.guess("https://yanolja.wd102.myworkdayjobs.com/ko-KR/External_Yanolja"))
            .isEqualTo("야놀자");
    }

    @Test
    @DisplayName("careers/recruit 같은 접두 라벨은 회사명이 아니라 걷어낸다")
    void recruitingLabelsAreStripped() {
        assertThat(CompanyNameGuesser.guess("https://careers.spotify.com/job/1")).isEqualTo("Spotify");
        assertThat(CompanyNameGuesser.guess("https://www.jobs.datadog.com/")).isEqualTo("Datadog");
        assertThat(CompanyNameGuesser.guess("https://recruit.example.co.kr/apply")).isEqualTo("Example");
    }

    @Test
    @DisplayName("스킴을 빼고 붙여넣어도 알아본다")
    void schemeIsOptional() {
        assertThat(CompanyNameGuesser.guess("careers.kakao.com/jobs")).isEqualTo("카카오");
    }

    @Test
    @DisplayName("사람인·원티드 같은 채용 사이트 주소는 회사를 알 수 없으므로 비워 둔다")
    void jobBoardsGiveUp() {
        assertThat(CompanyNameGuesser.guess("https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1")).isNull();
        assertThat(CompanyNameGuesser.guess("https://www.wanted.co.kr/wd/123456")).isNull();
        assertThat(CompanyNameGuesser.guess("https://www.jobkorea.co.kr/Recruit/GI_Read/1")).isNull();
        assertThat(CompanyNameGuesser.guess("https://career.programmers.co.kr/job_positions/1")).isNull();
        assertThat(CompanyNameGuesser.guess("https://www.linkedin.com/jobs/view/123")).isNull();
        assertThat(CompanyNameGuesser.guess("https://docs.google.com/forms/d/e/abc/viewform")).isNull();
    }

    @Test
    @DisplayName("주소가 비었거나 회사를 읽을 수 없으면 null (사용자가 직접 적는다)")
    void unreadableUrlsGiveUp() {
        assertThat(CompanyNameGuesser.guess(null)).isNull();
        assertThat(CompanyNameGuesser.guess("   ")).isNull();
        assertThat(CompanyNameGuesser.guess("그냥 메모")).isNull();
        assertThat(CompanyNameGuesser.guess("https://192.168.0.10/jobs")).isNull();
    }
}
